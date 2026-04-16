#ifndef BIGNUM_TOMMATH_H_
#define BIGNUM_TOMMATH_H_

#include "tommath.h"

#if !defined(MP_64BIT)
#error "bignum-kt requires LibTomMath MP_64BIT digits"
#endif

#if MP_DIGIT_BIT != 60
#error "bignum-kt requires LibTomMath MP_DIGIT_BIT == 60"
#endif

#endif
