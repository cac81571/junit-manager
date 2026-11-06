package dev.junitmanager.examples.calc;

import org.junit.platform.suite.api.SelectMethod;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectMethod(type = CalculatorTest.class, name = "adds")
@SelectMethod(type = CalculatorTest.class, name = "subtracts")
@SelectMethod(type = CalculatorTest.Div.class, name = "divides")
class CalculatorMethods {
}
