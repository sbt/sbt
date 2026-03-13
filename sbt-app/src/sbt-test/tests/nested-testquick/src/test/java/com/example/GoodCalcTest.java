package com.example;

import static org.junit.Assert.*;
import org.junit.Test;

public class GoodCalcTest {
  @Test
  public void testAddPositive() {
    assertEquals(3, Calc.add(1, 2));
  }

  public static class Nested {
    @Test
    public void testAddZero() {
      assertEquals(0, Calc.add(0, 0));
    }
  }
}
