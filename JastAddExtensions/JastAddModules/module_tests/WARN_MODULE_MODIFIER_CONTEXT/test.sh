#!/bin/sh
../testlib/pretest.sh
../testlib/ja-modules.sh -debug -d classes -instance-module m1 *.java *.module ../testlib/*.module 2>&1 > out
../testlib/posttestclasses.sh
