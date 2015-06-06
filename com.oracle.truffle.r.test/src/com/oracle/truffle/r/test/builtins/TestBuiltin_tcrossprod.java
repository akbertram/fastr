/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.*;

import com.oracle.truffle.r.test.*;

// Checkstyle: stop line length check
public class TestBuiltin_tcrossprod extends TestBase {

    @Test
    public void testtcrossprod1() {
        assertEval(Ignored.Unknown,
                        "argv <- list(structure(c(5, 2, 0, 2, 5, 2, 0, 2, 5), .Dim = c(3L, 3L)), structure(c(0, 1, 0, 0, 2, 0, 0, 2, 0, 1, 2, 0, 1, 0, 0), .Dim = c(5L, 3L), .Dimnames = list(c('a', 'b', 'c', 'd', 'e'), NULL))); .Internal(tcrossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testtcrossprod2() {
        assertEval(Ignored.Unknown,
                        "argv <- list(structure(c(5, 2, 0, 2, 5, 2, 0, 2, 5), .Dim = c(3L, 3L), .Dimnames = list(c('A', 'B', 'C'), c('A', 'B', 'C'))), structure(c(0, 1, 0, 0, 2, 0, 0, 2, 0, 1, 2, 0, 1, 0, 0), .Dim = c(5L, 3L), .Dimnames = list(NULL, c('A', 'B', 'C')))); .Internal(tcrossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testtcrossprod3() {
        assertEval(Ignored.Unknown,
                        "argv <- list(structure(c(-1.67712982705863, -1.48498667828929, -1.66855080519244, -1.58355627712096, -1.82359988031979, -1.9949008033236, -0.0318360538544526, -0.560218641212122, 0.066207937805176, 0.499775901814107, -0.00128363357081381, -0.00713986667014182, 0.296776079992064, -0.138150806520963, 0.253601178172029, -0.170225064799926, -0.240191246767341, -0.00408674943172847, -0.242382276284081, 0.0729153527553058, 0.269807081327349, 0.0299339639014576, -0.077267349576335, -0.0293027062153706, -0.0099926992270607, 0.0334924583850379, -0.0453336990810482, 0.0438958486872448, -0.112099180250145, 0.089015596249019), .Dim = c(6L, 5L)), structure(c(-0.399602067979347, -0.353820997034499, -0.397557983601584, -0.377306725388702, -0.434500818950138, -0.47531590790431, -0.0422023061126668, -0.742633452454394, 0.087766143100416, 0.662509734796894, -0.00170160212505116, -0.00946470439441127, 0.584095849615428, -0.271899651454647, 0.499121747385523, -0.335026171424641, -0.472729171281292, -0.00804328091925277, -0.637436340955898, 0.191758639997983, 0.70956029179181, 0.0787227379500612, -0.2032038701195, -0.0770626058818733, -0.062340518587102, 0.208946269374942, -0.282819110829524, 0.273848927982668, -0.699342677207614, 0.555333279468297), .Dim = c(6L, 5L))); .Internal(tcrossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testtcrossprod4() {
        assertEval(Ignored.Unknown, "argv <- list(c(2, 3), structure(c(0, 0, 1, 0), .Dim = c(2L, 2L), .Dimnames = list(NULL, NULL))); .Internal(tcrossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testtcrossprod5() {
        assertEval(Ignored.Unknown,
                        "argv <- list(structure(c(-0.106539372596213, -2.87400113021639, 0.341152775291742, 1.88577541025803, 0.842201032082677, -1.43117364207636, -0.69348461621825, -2.41970841038843, -3.02719090531729, -0.226641199170227, -0.332680183991575, -1.62371869115524, -1.66979618600051, -1.10431770731054, 1.88731633228519, 2.05877121721062, -0.223853590000374, 2.00359231906507, 2.73128102907396, 0.539089155601206, -0.199828039026098, -1.05787977326062, 0.306997029957149, 1.45711168105796, 1.49857746263809, -0.757919845814536, 0.268252398125501, -0.535834002202256, -0.271560453176356, -2.05847896960824, 0.980553291004929, 0.685818887220841, -0.522933983909647, -0.337189871316714, 0.191459457586776, 1.89272736696455, -0.453746315234956, 0.612338437255857, 1.37687299952389, -1.15071450872488, -0.20817483353688, -0.081142998844394, -0.253631714967276, -0.410462721238244, -0.68459626706876, -0.624834027577721, 0.00753430632431097, -0.0556623066116985, -0.563702942039652, 0.0408500401240061, -0.420302429975138, 0.033747665813787, 0.339840694442255, -0.250248532584852, -0.31434827109732, 0.378366203759376, -0.193977362697154, -0.518701418701189, 1.24086430187875, 0.0626244079886504, -0.168813902431602, -0.233723461170579, -0.0800002226605061, -0.0555238917407563, -0.947495254278566, -0.0485572234634504, -0.0296030565974314, -0.095544458696536, 0.0295824037592777, 0.194854132525369, 0.267233992325682, -0.087254491408015, 0.126110082843019, 0.159157280802928, -0.155595903815538, 0.170585777111235, -0.160659663851048, -0.059679874503493, 0.0114766797349517, -0.288711739670586, 0.192267902822735, -0.558695699349865, -0.0862396209998433, 0.00725278175306798, -0.128294571915242, -0.130284537275488, -0.0857140300930927, -0.0514859262258765, -0.0490801347386973, 0.0204665694600954, -0.14875269796722, 0.196176132315475, -0.0529883263026191, -0.132778199491125, -0.228017010951841, 0.0529472898389869), .Dim = c(12L, 8L)), structure(c(-0.0185462290552347, -0.500302207222603, 0.059387411050087, 0.328273218183171, 0.146609210012521, -0.249136760776327, -0.120720858638907, -0.421219548532773, -0.526969274886959, -0.0394533916498165, -0.057912513881884, -0.282654740999492, -0.321354169237256, -0.212527194864884, 0.363216168017541, 0.396212855019715, -0.0430808772043306, 0.385593613508892, 0.525638130815129, 0.103748319223306, -0.0384571326787796, -0.203590161804909, 0.0590819264933657, 0.28042279511599, 0.416779971858557, -0.210790446196582, 0.0746055707690415, -0.149024582263218, -0.0755255973444945, -0.572498138010465, 0.272708607475933, 0.190737938906932, -0.145436866983219, -0.0937782587701373, 0.0532481432121619, 0.52639978807016, -0.204031585044791, 0.275344124552216, 0.61912476435818, -0.517430328944052, -0.0936079034183924, -0.0364867638890611, -0.114048046419078, -0.184568682552654, -0.307835583002935, -0.280962892162748, 0.0033878764630103, -0.0250291148686315, -0.338839482109357, 0.0245547883601272, -0.252642033739931, 0.0202855808510911, 0.204276820851501, -0.150423346865756, -0.188953431740463, 0.227434343460447, -0.116598981866977, -0.311789254542753, 0.745878344887204, 0.0376432698639114, -0.159472254176866, -0.220789915226282, -0.0755732534972413, -0.0524513683353668, -0.895063746796005, -0.0458702143055051, -0.0279649134231136, -0.0902573187573718, 0.0279454034502495, 0.184071497085901, 0.252446075441722, -0.0824260930614398, 0.167922807083695, 0.211927046257706, -0.207184868571959, 0.227144744458931, -0.21392755544038, -0.0794671752416819, 0.0152818571968794, -0.384436237535829, 0.256015738204522, -0.743935362069355, -0.114833000769291, 0.00965749481472171, -0.300640883681616, -0.30530409690622, -0.200859174058623, -0.120650267011609, -0.115012621802916, 0.0479606224687302, -0.348581720171566, 0.459712089888966, -0.124170937293853, -0.311147655218321, -0.534326859224339, 0.124074773921503), .Dim = c(12L, 8L))); .Internal(tcrossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testtcrossprod6() {
        assertEval(Ignored.Unknown,
                        "argv <- list(structure(c(-1.22589324018138, -0.740548974281808, -0.54768368397833, -0.441021701509591, -0.370068251595057, -0.319690799411412, -0.282987166340516, -0.254112864677485, -0.230083320312515, 0.203647970189376, -0.0305516337408725, -0.0825170335109532, -0.0984577177107505, -0.100129992839015, -0.0988979642888749, -0.0945771185256416, -0.0902309571831907, -0.0871241228998968, -0.00955870050771132, 0.0197754782700907, 0.0125304440435148, 0.00419858922572787, -0.00191073996840182, -0.0061756059258365, -0.00956682744689523, -0.0127366531032827, -0.0131079781713544, 0.000214464770644159, -0.000956552371122151, 5.72249143534175e-05, 0.00029865136977495, 0.00077852017665313, 0.00142425180877207, 0.000491677810053133, -0.000120006753650731, -0.00247588122373662, 4.2574997724815e-05, -0.000297064220851874, 0.000399761711902461, 5.67830351414009e-05, -0.00026523273781528, 0.000320119491527155, -0.00026454073650643, -0.000195756422133707, 0.000192249930248858, -4.94461924222768e-07, 2.80125995838013e-05, -0.000119138513940463, 0.000151917649712048, -7.31975645151543e-05, 4.92140187851149e-05, -1.13604576670922e-05, -3.74519303853871e-05, 9.55915555684852e-06), .Dim = c(9L, 6L)), structure(c(-0.709851441473678, -0.428813651666777, -0.317135326144804, -0.255372882626744, -0.214287405483635, -0.185116425598763, -0.163863247924954, -0.147143631578904, -0.133229363887123, 0.633337192677659, -0.0950143815681878, -0.256624734846691, -0.306199636924392, -0.311400346924765, -0.307568786499592, -0.294131125799441, -0.280614734641737, -0.270952601985731, -0.28505721606605, 0.58973945020027, 0.373679821042009, 0.125209295460755, -0.0569816174886273, -0.184167401344961, -0.285299575647986, -0.379829336915808, -0.390902901787376, 0.0675695685124445, -0.301372718615498, 0.0180293609967187, 0.0940935153626058, 0.245281648154537, 0.448726753036158, 0.154908693733931, -0.0378094944843564, -0.780054577138554, 0.056333641054865, -0.393064241503382, 0.528949712019966, 0.0751331835725979, -0.350946016360591, 0.423570111428232, -0.350030386168567, -0.259017559788085, 0.254377901167792, -0.00226968135332679, 0.128583560874789, -0.546870143699694, 0.697333080468545, -0.335991790571385, 0.225902410856869, -0.0521468239901137, -0.171912019667483, 0.0438784789244046), .Dim = c(9L, 6L))); .Internal(tcrossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testtcrossprod7() {
        assertEval(Ignored.Unknown, "argv <- list(0, 0); .Internal(tcrossprod(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testtcrossprod8() {
        assertEval(Ignored.Unknown,
                        "argv <- list(structure(c(1.1173625565162, 1.46907016195074, 1.1173625565162, -0.59596185089264, -1.32605913508878e-308, 0.595961850892641), .Dim = c(3L, 2L)), structure(c(0.517876924314756, 0.680886908762812, 0.517876924314755, -0.707106781186547, -1.57336481399136e-308, 0.707106781186548), .Dim = c(3L, 2L))); .Internal(tcrossprod(argv[[1]], argv[[2]]))");
    }
}