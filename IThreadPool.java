package com.test4;

/**
 * @Author:pjq
 * @Date: 2019/9/27 11:40
 */
public interface IThreadPool {
     /**
      * 初始化线程
      */
     void init();

     /**
      * 创建线程
      */
     void createWorkTask();

     /**
      * 提交
      * @param runnable
      */
     void submit(Runnable runnable);
}
