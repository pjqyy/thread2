package com.test4;

/**
 * @Author:pjq
 * @Date: 2019/9/27 12:25
 */
public class MyAbortPolicy implements DiscardPolicy {
    // 拒绝任务的处理程序,丢弃。
    @Override
    public  void discard() throws RuntimeException {
        throw new RuntimeException("超出任务...") ;

    }

}

