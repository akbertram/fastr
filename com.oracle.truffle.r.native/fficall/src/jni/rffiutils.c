/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include <rffiutils.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <assert.h>

/*
 * All calls pass through one of the call(N) methods in rfficall.c, which carry the JNIEnv value,
 * that needs to be saved for reuse in the many R functions such as Rf_allocVector.
 * Currently only single threaded access is permitted (via a semaphore in CallRFFIWithJNI)
 * so we are safe to use static variables. TODO Figure out where to store such state
 * (portably) for MT use. JNI provides no help.
 */
jclass UpCallsRFFIClass;
jobject UpCallsRFFIObject;
jclass CharSXPWrapperClass;

static JNIEnv *curenv = NULL;

// default for trace output when enabled
FILE *traceFile = NULL;

typedef struct globalRefTable_struct {
    int permanent;
    SEXP gref;         // The jobject (SEXP) global ref
} GlobalRefElem;

#define CACHED_GLOBALREFS_INITIAL_SIZE 64
static GlobalRefElem *cachedGlobalRefs;
static int cachedGlobalRefsHwm;
static int cachedGlobalRefsLength;

// Data structure for managing the required copying of
// Java arrays to return C arrays, e.g, int*.
// N.B. There are actually two levels to this as FastR
// wraps, e.g.,  int[] in an RIntVector.
typedef struct nativeArrayTable_struct {
    SEXPTYPE type;
    SEXP obj;         // The jobject (SEXP) that data is derived from (e.g, RIntVector)
    void *jArray;     // the jarray corresponding to obj
    void *data;       // the (possibly) copied (or pinned) data from JNI GetXXXArrayElements
} NativeArrayElem;

#define NATIVE_ARRAY_TABLE_INITIAL_SIZE 64
// A table of vectors that have been accessed and whose contents, e.g. the actual data
// as a primitive array have been copied and handed out to the native code.
static NativeArrayElem *nativeArrayTable;
// hwm of nativeArrayTable
static int nativeArrayTableHwm;
static int nativeArrayTableLastIndex;
static int nativeArrayTableLength;
static void releaseNativeArray(JNIEnv *env, int index);
static NativeArrayElem *findNativeArray(JNIEnv *env, SEXP x);
static void updateNativeArray(JNIEnv *env, int index);

static jfieldID CharSXPWrapperContentsFieldID;
extern jmethodID logNotCharSXPWrapperMethodID;

static int isEmbedded = 0;
void setEmbedded() {
    isEmbedded = 1;
}

#ifdef TRACE_ENABLED
// Helper for debugging purposes: prints out (into the trace file) the java
// class name for given SEXP
static void fastRInspect(JNIEnv *env, SEXP v) {
    // this invokes getClass().getName()
    jclass cls = (*env)->GetObjectClass(env, v);
    jmethodID getClassMethodID = checkGetMethodID(env, cls, "getClass", "()Ljava/lang/Class;", 0);
    jobject classObj = (*env)->CallObjectMethod(env, v, getClassMethodID);
    jclass javaClassClass = (*env)->GetObjectClass(env, classObj);
    jmethodID getNameMethodID = checkGetMethodID(env, javaClassClass, "getName", "()Ljava/lang/String;", 0);
    jstring nameJString = (jstring)(*env)->CallObjectMethod(env, javaClassClass, getNameMethodID);

    // This gets us the C string
    const char* result = (*env)->GetStringUTFChars(env, nameJString, NULL);
    fprintf(traceFile, "fastRInspect(%p): %s\n",  v, result);

    // Release the memory
    (*env)->ReleaseStringUTFChars(env, nameJString, result);
}
#endif

static int isValidJNIRef(JNIEnv *env, SEXP ref) {
#if VALIDATE_REFS
    return (*env)->GetObjectRefType(env, ref) != JNIInvalidRefType;
#else
    return TRUE;
#endif
}

static jboolean fast_IsSameObject(jobject a, jobject b) {
    // this takes some assumptions about jni handles, but it is much faster
    void** pA = (void**) a;
    void** pB = (void**) b;
    if (pA == NULL && pB == NULL) {
        return TRUE;
    } else if (pA == NULL || pB == NULL) {
        return FALSE;
    } else {
        return *pA == *pB;
    }
}

// native down call depth, indexes nativeArrayTableHwmStack
int callDepth = 0;

#define CALLDEPTH_STACK_SIZE 16
static int nativeArrayTableHwmStack[CALLDEPTH_STACK_SIZE];

// stack of jmp_buf ptrs for non-local control transfer on error
static jmp_buf* callErrorJmpBufTable[CALLDEPTH_STACK_SIZE];


void init_utils(JNIEnv *env, jobject upCallsInstance) {
    curenv = env;
    UpCallsRFFIClass = (*env)->NewGlobalRef(env, (*env)->GetObjectClass(env, upCallsInstance));
    UpCallsRFFIObject = (*env)->NewGlobalRef(env, upCallsInstance);
    if (TRACE_ENABLED && traceFile == NULL) {
        if (!isEmbedded) {
            traceFile = stdout;
        } else {
            jclass RFFIUtilsClass = checkFindClass(env, "com/oracle/truffle/r/runtime/ffi/RFFIUtils");
            jclass FileDescriptorClass = checkFindClass(env, "java/io/FileDescriptor");
            jmethodID getTraceFileDescriptorMethodID = checkGetMethodID(env, RFFIUtilsClass, "getTraceFileDescriptor", "()Ljava/io/FileDescriptor;", 1);
            // ASSUMPTION: FileDescriptor has an "fd" field
            jobject tfd = (*env)->CallStaticObjectMethod(env, RFFIUtilsClass, getTraceFileDescriptorMethodID);
            jfieldID fdField = checkGetFieldID(env, FileDescriptorClass, "fd", "I", 0);
            int fd = (*env)->GetIntField(env, tfd, fdField);
            traceFile = fdopen(fd, "w");
            if (traceFile == NULL) {
                fprintf(stderr, "%s, %d", "failed to fdopen trace file on JNI side\n", errno);
                exit(1);
            }
            // no buffering
            setvbuf(traceFile, (char*) NULL, _IONBF, 0);
        }
    }
    cachedGlobalRefs = calloc(CACHED_GLOBALREFS_INITIAL_SIZE, sizeof(GlobalRefElem));
    cachedGlobalRefsLength = CACHED_GLOBALREFS_INITIAL_SIZE;
    cachedGlobalRefsHwm = 0;
    nativeArrayTable = calloc(NATIVE_ARRAY_TABLE_INITIAL_SIZE, sizeof(NativeArrayElem));
    nativeArrayTableLength = NATIVE_ARRAY_TABLE_INITIAL_SIZE;
    nativeArrayTableHwm = 0;

	CharSXPWrapperClass = checkFindClass(env, "com/oracle/truffle/r/runtime/ffi/CharSXPWrapper");
	CharSXPWrapperContentsFieldID = checkGetFieldID(env, CharSXPWrapperClass, "contents", "Ljava/lang/String;", 0);
}

const char *stringToChars(JNIEnv *jniEnv, jstring string) {
    // This is nasty:
    // 1. the resulting character array has to be copied and zero-terminated.
    // 2. It causes an (inevitable?) memory leak
    jsize len = (*jniEnv)->GetStringUTFLength(jniEnv, string);
    const char *stringChars = (*jniEnv)->GetStringUTFChars(jniEnv, string, NULL);
    char *copyChars = malloc(len + 1);
    memcpy(copyChars, stringChars, len);
    copyChars[len] = 0;
    return copyChars;
}

void callEnter(JNIEnv *env, jmp_buf *jmpbuf) {
    setEnv(env);
    //printf("callEnter: callDepth %d, jmpbufptr %p\n", callDepth, jmpbuf);
    callErrorJmpBufTable[callDepth] = jmpbuf;
    if (callDepth >= CALLDEPTH_STACK_SIZE) {
        fatalError("call stack overflow\n");
    }
    nativeArrayTableHwmStack[callDepth] = nativeArrayTableHwm;
    callDepth++;
}

jmp_buf *getErrorJmpBuf() {
    // printf("getErrorJmpBuf: callDepth %d, jmpbufptr %p\n", callDepth, callErrorJmpBufTable[callDepth - 1]);
    return callErrorJmpBufTable[callDepth - 1];
}

void callExit(JNIEnv *env) {
    int oldHwm = nativeArrayTableHwmStack[callDepth - 1];
    for (int i = oldHwm; i < nativeArrayTableHwm; i++) {
        releaseNativeArray(env, i);
    }
    nativeArrayTableHwm = oldHwm;
    callDepth--;
}

void invalidateNativeArray(JNIEnv *env, SEXP oldObj) {
    assert(isValidJNIRef(env, oldObj));
    for (int i = 0; i < nativeArrayTableHwm; i++) {
        NativeArrayElem cv = nativeArrayTable[i];
        if (fast_IsSameObject(cv.obj, oldObj)) {
#if TRACE_NATIVE_ARRAYS
            fprintf(traceFile, "invalidateNativeArray(%p): found\n", oldObj);
#endif
            releaseNativeArray(env, i);
        }
    }
#if TRACE_NATIVE_ARRAYS
    fprintf(traceFile, "invalidateNativeArray(%p): not found\n", oldObj);
#endif
}

void updateJObjects(JNIEnv *env) {
    int oldHwm = nativeArrayTableHwmStack[callDepth - 1];
    for (int i = oldHwm; i < nativeArrayTableHwm; i++) {
        NativeArrayElem cv = nativeArrayTable[i];
        if (cv.obj != NULL) {
            updateJObject(env, cv.obj);
        }
    }
}

// Updates the data on the Java side from the data on the native side
void updateJObject(JNIEnv *env, SEXP x) {
#if TRACE_NATIVE_ARRAYS
    fprintf(traceFile, "updateJObject(%p)\n", x);
#endif
	NativeArrayElem *cv = findNativeArray(env, x);
	if (cv != NULL && cv->data != NULL && cv->type != CHARSXP) {
#if TRACE_NATIVE_ARRAYS
        fprintf(traceFile, "updateJObject(%p): updating\n", x);
#endif
        int len = (*env)->GetArrayLength(env, cv->jArray);
        switch (cv->type) {
            case INTSXP:
                (*env)->SetIntArrayRegion(env, cv->jArray, 0, len, cv->data);
                break;
            case REALSXP:
                (*env)->SetDoubleArrayRegion(env, cv->jArray, 0, len, cv->data);
                break;
            case RAWSXP:
                (*env)->SetByteArrayRegion(env, cv->jArray, 0, len, cv->data);
                break;
            case LGLSXP: {
                int *data = (int*) cv->data;
                jbyte *byteData = malloc(len * sizeof(jbyte));
                for (int i = 0; i < len; ++i) {
                    byteData[i] = data[i];
                }
                (*env)->SetByteArrayRegion(env, cv->jArray, 0, len, byteData);
                free(byteData);
                break;
            }
            default:
                fatalError("updateJObject: unexpected type");
        }
    }
}

static NativeArrayElem *findNativeArray(JNIEnv *env, SEXP x) {
    if (nativeArrayTableLastIndex < nativeArrayTableHwm) {
        NativeArrayElem *cv = &nativeArrayTable[nativeArrayTableLastIndex];
        if (cv->obj != NULL && (cv->obj == x || fast_IsSameObject(cv->obj, x))) {
            void *data = cv->data;
#if TRACE_NATIVE_ARRAYS
            fprintf(traceFile, "findNativeArray(%p): found %p (cached)\n", x, data);
#endif
            return cv;
        }
    }
    int i;
    assert(isValidJNIRef(env, x));
    for (i = 0; i < nativeArrayTableHwm; i++) {
        NativeArrayElem *cv = &nativeArrayTable[i];
        if (cv->obj != NULL) {
            assert(isValidJNIRef(env, cv->obj));
            if (fast_IsSameObject(cv->obj, x)) {
                nativeArrayTableLastIndex = i;
                void *data = cv->data;
#if TRACE_NATIVE_ARRAYS
                fprintf(traceFile, "findNativeArray(%p): found %p\n", x, data);
#endif
                return cv;
            }
        }
    }
#if TRACE_NATIVE_ARRAYS
    fprintf(traceFile, "findNativeArray(%p): not found\n", x);
#endif
    return NULL;
}


static void addNativeArray(JNIEnv *env, SEXP x, SEXPTYPE type, void *jArray, void *data) {
#if TRACE_NATIVE_ARRAYS
    fprintf(traceFile, "addNativeArray(x=%p, t=%p, ix=%d, type=%d)\n", x, data, nativeArrayTableHwm, type);
#endif
    // check for overflow
    if (nativeArrayTableHwm >= nativeArrayTableLength) {
        int newLength = 2 * nativeArrayTableLength;
        NativeArrayElem *newnativeArrayTable = calloc(newLength, sizeof(NativeArrayElem));
        if (newnativeArrayTable == NULL) {
            fatalError("FFI copied vectors table expansion failure");
        }
        memcpy(newnativeArrayTable, nativeArrayTable, nativeArrayTableLength * sizeof(NativeArrayElem));
        free(nativeArrayTable);
        nativeArrayTable = newnativeArrayTable;
        nativeArrayTableLength = newLength;
    }
    nativeArrayTable[nativeArrayTableHwm].obj = x;
    nativeArrayTable[nativeArrayTableHwm].data = data;
    nativeArrayTable[nativeArrayTableHwm].type = type;
    nativeArrayTable[nativeArrayTableHwm].jArray = jArray;
    nativeArrayTableHwm++;
}

static void* extractVectorNativeArray(JNIEnv *thisenv, jarray jArray, SEXPTYPE type) {
    jboolean isCopy;
    int len = (*thisenv)->GetArrayLength(thisenv, jArray);
    switch (type) {
        case INTSXP:
            return (*thisenv)->GetIntArrayElements(thisenv, (jintArray) jArray, &isCopy);

        case REALSXP:
            return (*thisenv)->GetDoubleArrayElements(thisenv, (jdoubleArray) jArray, &isCopy);

        case RAWSXP:
            return (*thisenv)->GetByteArrayElements(thisenv, (jbyteArray) jArray, &isCopy);

        case LGLSXP: {
            // Special treatment because R FFI wants int* and FastR represents using byte[]
            jbyteArray byteArray = (jbyteArray) jArray;
            jbyte* internalData = (*thisenv)->GetByteArrayElements(thisenv, byteArray, &isCopy);
            int* idata = malloc(len * sizeof(int));
            for (int i = 0; i < len; i++) {
                char value = internalData[i];
                idata[i] = value == 0 ? FALSE : value == 1 ? TRUE : NA_INTEGER;
            }
            (*thisenv)->ReleaseByteArrayElements(thisenv, byteArray, internalData, JNI_ABORT);
            return idata;
        }
    }
}

static jarray getJArray(JNIEnv *thisenv, SEXP x, SEXPTYPE type) {
   switch (type) {
       case INTSXP:
          return (*thisenv)->CallObjectMethod(thisenv, UpCallsRFFIObject, INTEGER_MethodID, x);
       case REALSXP:
          return (*thisenv)->CallObjectMethod(thisenv, UpCallsRFFIObject, REAL_MethodID, x);
       case RAWSXP:
          return (*thisenv)->CallObjectMethod(thisenv, UpCallsRFFIObject, RAW_MethodID, x);
       case LGLSXP:
          return (*thisenv)->CallObjectMethod(thisenv, UpCallsRFFIObject, LOGICAL_MethodID, x);
       default:
          fatalError("getNativeArray: unexpected type");
   }
}

void *getNativeArray(JNIEnv *thisenv, SEXP x, SEXPTYPE type) {
#if TRACE_NATIVE_ARRAYS
    fprintf(traceFile, "getNativeArray(%p)\n", x);
#endif
    NativeArrayElem *cv = findNativeArray(thisenv, x);
    void *data = NULL;
    if (cv != NULL) {
        data = cv->data;
    }
    if (data == NULL) {
        jarray jArray;
        switch (type) {
            case INTSXP:
            case REALSXP:
            case RAWSXP:
            case LGLSXP: {
                jArray = getJArray(thisenv, x, type);
                data = extractVectorNativeArray(thisenv, jArray, type);
                break;
            }
            case CHARSXP: {
                jstring string = stringFromCharSXP(thisenv, x);
                data = (void *) stringToChars(thisenv, string);
                jArray = string;
                break;
            }
            default:
                fatalError("getNativeArray: unexpected type");
        }
        addNativeArray(thisenv, x, type, jArray, data);
    }
    return data;
}

void updateNativeArrays(JNIEnv *env) {
    int oldHwm = nativeArrayTableHwmStack[callDepth - 1];
    for (int i = oldHwm; i < nativeArrayTableHwm; i++) {
        updateNativeArray(env, i);
    }
}

int getTypeSize(SEXPTYPE type) {
   switch (type) {
        case INTSXP:
        case LGLSXP:
            return sizeof(jint);
        case RAWSXP:
            return sizeof(jbyte);
        case REALSXP:
           return sizeof(jdouble);
        default:
           fatalError("getNativeArray: unexpected type");
   }
}

static void updateNativeArray(JNIEnv *env, int i) {
    NativeArrayElem cv = nativeArrayTable[i];
    if (cv.obj != NULL && cv.type != CHARSXP) {
        jarray current = getJArray(env, cv.obj, cv.type);
        if (fast_IsSameObject(current, cv.jArray)) {
#if TRACE_NATIVE_ARRAYS
            fprintf(traceFile, "updateNativeArray(x=%p, t=%p, ix=%d, type=%d): copying data from Java\n", cv.obj, cv.data, i, cv.type);
#endif
            // same array, copy back the contents
            int len = (*env)->GetArrayLength(env, cv.jArray);
            switch (cv.type) {
                case INTSXP:
                    (*env)->GetIntArrayRegion(env, cv.jArray, 0, len, cv.data);
                    break;
                case REALSXP:
                    (*env)->GetDoubleArrayRegion(env, cv.jArray, 0, len, cv.data);
                    break;
                case RAWSXP:
                    (*env)->GetByteArrayRegion(env, cv.jArray, 0, len, cv.data);
                    break;
                case LGLSXP: {
                    jbyte *byteData = malloc(len * sizeof(jbyte));
                    (*env)->GetByteArrayRegion(env, cv.jArray, 0, len, byteData);
                    for (int i = 0; i < len; ++i) {
                        ((int*) cv.data)[i] = byteData[i];
                    }
                    free(byteData);
                    break;
                }
                default:
                    fatalError("updateJObject: unexpected type");
            }
        } else {
            // not the same array: this could happen if temporary vector got re-used for re-allocated copy.
            // If the user now attempts at accessing a previously acquired data pointer, it's an error anyway,
            // We invalidate the native "mirror", it's not reflecting the same jobject, anymore and if user now
            // attempts to get the data pointer, she should get the right one -- for the new array, not the old one.
#if TRACE_NATIVE_ARRAYS
            fprintf(traceFile, "updateNativeArray(x=%p, t=%p, ix=%d, type=%d): data in Java have changed, invalidating the cached pointer.\n", cv.obj, cv.data, index, cv.type);
#endif
            nativeArrayTable[i].obj = NULL;
        }
    }
}

// Updates the Java counterpart object with the contents of the native array and
// releases the native array. Use updateJObject to only update the java counterpart,
// but do not release.
static void releaseNativeArray(JNIEnv *env, int i) {
    NativeArrayElem cv = nativeArrayTable[i];
#if TRACE_NATIVE_ARRAYS
    fprintf(traceFile, "releaseNativeArray(x=%p, ix=%d, type=%d)\n", cv.obj, i, cv.type);
#endif
    if (cv.obj != NULL) {
        assert(isValidJNIRef(env, cv.obj));
        jboolean complete = JNI_FALSE; // pessimal
        switch (cv.type) {
        case INTSXP: {
            jintArray intArray = (jintArray) cv.jArray;
            (*env)->ReleaseIntArrayElements(env, intArray, (jint *)cv.data, 0);
            break;
        }

        case LGLSXP: {
            // for LOGICAL, we need to convert back to 1-byte elements
            jintArray byteArray = (jbyteArray) cv.jArray;
            int len = (*env)->GetArrayLength(env, byteArray);
            jbyte* internalData = (*env)->GetByteArrayElements(env, byteArray, NULL);
            int* data = (int*) cv.data;
            complete = JNI_TRUE; // since we going to look at each element anyway
            for (int i = 0; i < len; i++) {
                int isNA = data[i] == NA_INTEGER ? JNI_TRUE : JNI_FALSE;
                if (isNA) {
                    internalData[i] = 255;
                    complete = JNI_FALSE;
                } else {
                    internalData[i] = (jbyte) data[i];
                }

            }
            // note: internalData is used only as temp array here, no need to honor "mode"
            (*env)->ReleaseByteArrayElements(env, byteArray, internalData, 0);
            free(data); // was malloc'ed in addNativeArray
            break;
        }

        case REALSXP: {
            jdoubleArray doubleArray = (jdoubleArray) cv.jArray;
            (*env)->ReleaseDoubleArrayElements(env, doubleArray, (jdouble *)cv.data, 0);
            break;

        }

        case RAWSXP: {
            jbyteArray byteArray = (jbyteArray) cv.jArray;
            (*env)->ReleaseByteArrayElements(env, byteArray, (jbyte *)cv.data, 0);
            break;

        }

        case CHARSXP: {
            jstring string = (jstring) cv.jArray;
            (*env)->ReleaseStringUTFChars(env, string, (const char *)cv.data);
            break;

        }
        default:
            fatalError("updateManagedVector type");
        }
        // update complete status
        (*env)->CallStaticVoidMethod(env, JNIUpCallsRFFIImplClass, setCompleteMethodID, cv.obj, complete);

        nativeArrayTable[i].obj = NULL;
    }
#if TRACE_NATIVE_ARRAYS
    fprintf(traceFile, "updateManagedVector(x=%p, ix=%d): DONE\n", cv.obj, i);
#endif
}

static SEXP findCachedGlobalRef(JNIEnv *env, SEXP obj) {
    // TODO: this assert fails in test RFFI: assert(isValidJNIRef(env, obj));
    for (int i = 0; i < cachedGlobalRefsHwm; i++) {
        GlobalRefElem elem = cachedGlobalRefs[i];
        if (elem.gref == NULL) {
            continue;
        }
        if (fast_IsSameObject(elem.gref, obj)) {
#if TRACE_REF_CACHE
            fprintf(traceFile, "gref: cache hit: %d\n", i);
#endif
            return elem.gref;
        }
    }
    return NULL;
}

SEXP addGlobalRef(JNIEnv *env, SEXP obj, int permanent) {
    SEXP gref;
    assert(isValidJNIRef(env, obj));
    if (cachedGlobalRefsHwm >= cachedGlobalRefsLength) {
        int newLength = cachedGlobalRefsLength * 2;
#if TRACE_REF_CACHE
        fprintf(traceFile, "gref: extending table to %d\n", newLength);
#endif
        SEXP newCachedGlobalRefs = calloc(newLength, sizeof(GlobalRefElem));
        if (newCachedGlobalRefs == NULL) {
            fatalError("FFI global refs table expansion failure");
        }
        memcpy(newCachedGlobalRefs, cachedGlobalRefs, cachedGlobalRefsLength * sizeof(GlobalRefElem));
        free(cachedGlobalRefs);
        cachedGlobalRefs = newCachedGlobalRefs;
        cachedGlobalRefsLength = newLength;
    }
    gref = (*env)->NewGlobalRef(env, obj);
    cachedGlobalRefs[cachedGlobalRefsHwm].gref = gref;
    cachedGlobalRefs[cachedGlobalRefsHwm].permanent = permanent;
#if TRACE_REF_CACHE
            fprintf(traceFile, "gref: add: index %d, ref %p\n", cachedGlobalRefsHwm, gref);
#endif
    cachedGlobalRefsHwm++;
    return gref;
}

SEXP checkRef(JNIEnv *env, SEXP obj) {
    SEXP gref = findCachedGlobalRef(env, obj);
    TRACE(TARGpp, obj, gref);
    if (gref == NULL) {
        return obj;
    } else {
        return gref;
    }
}

SEXP createGlobalRef(JNIEnv *env, SEXP obj, int permanent) {
    SEXP gref = findCachedGlobalRef(env, obj);
    if (gref == NULL) {
        gref = addGlobalRef(env, obj, permanent);
    }
    return gref;
}

void releaseGlobalRef(JNIEnv *env, SEXP obj) {
    for (int i = 0; i < cachedGlobalRefsHwm; i++) {
        GlobalRefElem elem = cachedGlobalRefs[i];
        if (elem.gref == NULL || elem.permanent) {
            continue;
        }
        if (fast_IsSameObject(elem.gref, obj)) {
#if TRACE_REF_CACHE
            fprintf(traceFile, "gref: release: index %d, gref: %p\n", i, elem.gref);
#endif
            (*env)->DeleteGlobalRef(env, elem.gref);
            cachedGlobalRefs[i].gref = NULL;
        }
    }
}

void validateRef(JNIEnv *env, SEXP x, const char *msg) {
#ifdef VALIDATE_REFS
    jobjectRefType t = (*env)->GetObjectRefType(env, x);
    if (t == JNIInvalidRefType) {
        char buf[1000];
        sprintf(buf, "%s %p", msg,x);
        fatalError(buf);
    }
#endif
}

JNIEnv *getEnv() {
//    fprintf(traceFile, "getEnv()=%p\n", curenv);
    return curenv;
}

void setEnv(JNIEnv *env) {
//    printf("setEnv(%p)\n", env);
    curenv = env;
}

void *unimplemented(char *msg) {
    JNIEnv *thisenv = getEnv();
    char buf[1024];
    strcpy(buf, "unimplemented ");
    strcat(buf, msg);
    (*thisenv)->FatalError(thisenv, buf);
    return NULL;
}

void fatalError(char *msg) {
    JNIEnv *thisenv = getEnv();
    (*thisenv)->FatalError(thisenv, msg);
}

// Class/method search
jclass checkFindClass(JNIEnv *env, const char *name) {
    jclass klass = (*env)->FindClass(env, name);
    if (klass == NULL) {
        char buf[1024];
        strcpy(buf, "failed to find class ");
        strcat(buf, name);
        (*env)->FatalError(env, buf);
    }
    return (*env)->NewGlobalRef(env, klass);
}

jmethodID checkGetMethodID(JNIEnv *env, jclass klass, const char *name, const char *sig, int isStatic) {
    jmethodID methodID = isStatic ? (*env)->GetStaticMethodID(env, klass, name, sig) : (*env)->GetMethodID(env, klass, name, sig);
    if (methodID == NULL) {
        char buf[1024];
        strcpy(buf, "failed to find ");
        strcat(buf, isStatic ? "static" : "instance");
        strcat(buf, " method ");
        strcat(buf, name);
        strcat(buf, "(");
        strcat(buf, sig);
        strcat(buf, ")");
        (*env)->FatalError(env, buf);
    }
    return methodID;
}

jfieldID checkGetFieldID(JNIEnv *env, jclass klass, const char *name, const char *sig, int isStatic) {
    jfieldID fieldID = isStatic ? (*env)->GetStaticFieldID(env, klass, name, sig) : (*env)->GetFieldID(env, klass, name, sig);
    if (fieldID == NULL) {
        char buf[1024];
        strcpy(buf, "failed to find ");
        strcat(buf, isStatic ? "static" : "instance");
        strcat(buf, " field ");
        strcat(buf, name);
        strcat(buf, "(");
        strcat(buf, sig);
        strcat(buf, ")");
        (*env)->FatalError(env, buf);
    }
    return fieldID;
}

jstring stringFromCharSXP(JNIEnv *thisenv, SEXP charsxp) {
#if VALIDATE_REFS
	validateRef(thisenv, charsxp, "stringFromCharSXP");
	if (!(*thisenv)->IsInstanceOf(thisenv, charsxp, CharSXPWrapperClass)) {

	    (*thisenv)->CallStaticVoidMethod(thisenv, JNIUpCallsRFFIImplClass, logNotCharSXPWrapperMethodID, charsxp);
	    fatalError("only CharSXPWrapper expected in stringFromCharSXP");
	}
#endif
	return (*thisenv)->GetObjectField(thisenv, charsxp, CharSXPWrapperContentsFieldID);
}
