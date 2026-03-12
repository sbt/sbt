package com.example;

import static org.junit.Assert.*;
import org.junit.Test;

public class CalcTest {
  @Test
  public void testAdd() {
    assertEquals(3, Calc.add(1, 2));
  }

  public static class Nested {
    @Test
    public void testAddNegative() {
      assertEquals(-1, Calc.add(1, -2));
    }
  }
}
