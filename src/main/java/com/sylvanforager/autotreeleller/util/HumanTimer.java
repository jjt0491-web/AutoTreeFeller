package com.sylvanforager.autotreeleller.util;

public class HumanTimer {
 private int minTicks;
 private int maxTicks;
 private int current;
 private int elapsed;

 public HumanTimer(int minTicks, int maxTicks) {
 this.minTicks = minTicks;
 this.maxTicks = maxTicks;
 this.current = next();
 this.elapsed = 0;
 }

 private int next() {
 return minTicks + (int)(Math.random() * (maxTicks - minTicks));
 }

 public boolean tick() {
 elapsed++;
 if (elapsed >= current) {
 elapsed = 0;
 current = next();
 return true;
 }
 return false;
 }

 public void reset() {
 elapsed = 0;
 current = next();
 }
}