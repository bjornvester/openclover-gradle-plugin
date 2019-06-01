package com.github.bjornvester

import spock.lang.Specification

class MyGroovySpockTest extends Specification {
    def "My test"() {
        expect:
        new MyGroovyClass().doStuff() == true
    }
}
