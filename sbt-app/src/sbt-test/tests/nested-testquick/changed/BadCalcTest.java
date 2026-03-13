package com.example;

import static org.junit.Assert.*;
import org.junit.Test;

public class BadCalcTest {
  @Test
  public void testBadAdd() {
    assertEquals(99, Calc.add(1, 2));
  }

  public static class Nested {
    @Test
    public void testBadAddNegative() {
      assertEquals(99, Calc.add(1, -2));
    }
  }
}
