% Generated by roxygen2: do not edit by hand
% Please edit documentation in R/fastrInteropt.R
\name{is.external}
\alias{is.external}
\title{Determines whether the given object is an external object or not.}
\usage{
is.external(obj)
}
\arguments{
\item{obj}{an external object}
}
\value{
TRUE in case the given value is executable, otherwise FALSE.
}
\description{
Determines whether the given object is an external object or not.
}
\examples{
javaClass <- new.java.class('java.util.ArrayList')
is.external(javaClass)
}
