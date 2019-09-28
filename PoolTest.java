package com.test4;

import org.junit.Test;

import java.util.stream.IntStream;

/**
 * @Author:pjq
 * @Date: 2019/9/27 12:27
 */
public class PoolTest {
    public static void main(String[] args)  {
        MyThreadPool threadPool = new MyThreadPool();

        IntStream.range(0, 200).forEach(i -> threadPool.submit(() -> {
            System.out.println("任务" + Thread.currentThread().getName() + "  接收... " + i);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("任务" + Thread.currentThread().getName() + "  关闭... " + i);
        }));
    }
    @Test
    public  void  test() {
        IntStream.range(0,10).forEach(System.out::println);

    }
}
