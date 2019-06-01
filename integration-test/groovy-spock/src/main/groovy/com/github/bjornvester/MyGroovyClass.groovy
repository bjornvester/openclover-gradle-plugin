package com.github.bjornvester

class MyGroovyClass {
    boolean doStuff() {
        return new MyJavaClass().doStuff() && new MyJointlyCompiledJavaClass().doStuff()
    }
}
