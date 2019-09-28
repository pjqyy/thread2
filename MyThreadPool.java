package com.test4;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Author:pjq
 * @Date: 2019/9/23 9:30
 */
public class MyThreadPool extends Thread implements IThreadPool {
    /**
     * 默认核心线程池
     */
    private int coreSize = 4;
    /**
     * 最大线程池
     */
    private int maxSize = 12;

    /**
     * 当前线程的大小
     */
    private int size;

    /**
     * 活跃线程数
     */
    private int active;

    /**
     * 任务队列大小
     */
    private final int queueSize;

    /**
     * 默认任务队列大小
     */
    private final static int TASK_QUEUE_SIZE = 200;

    /**
     * 任务队列 正在执行任务的线程
     */
    private final static LinkedList<Runnable> TASK_QUEUE = new LinkedList<>();

    /**
     * 线程队列，可以取出空闲线程
     */
    private final static List<WorkerTask> THREAD_QUEUE = new ArrayList<>();

    /**
     * 线程池id
     */
    private static int seq = 0;

    /**
     * 线程池是否被销毁
     */
    private volatile boolean destroyed = false;
    //JVM首先会在主存中存储destroyed的状态为false；
    // 当线程1去取主存的flag相当于拷贝一份到线程内存进行修改，
    // 修改后的数据还没推送到主存中，这时候main线程从主存中得到的destroyed还是false状态；

    // 由于while（true）的执行效率非常高，main线程会一直在自己内存中取；

    //可见性   禁止指令重排序方法
    //voltile类修饰符，不同线程访问和修改的变量，如果不加
    //无法编译多线程程序，编译器失去大量优化的机会


    @Override
    public String toString() {
        return "MyThreadPool{" +
                "coreSize=" + coreSize +
                ", active=" + active +
                ", maxSize=" + maxSize +
                ", size=" + size +
                ", queueSize=" + TASK_QUEUE.size() +
                '}';
    }



    //队列满了丢异常
    private DiscardPolicy discardPolicy = new MyAbortPolicy();

    public MyThreadPool() {
        this(4, 8 ,12, TASK_QUEUE_SIZE);
    }

    public MyThreadPool(int coreSize,int active, int maxSize, int queue_size) {
        this.queueSize = queue_size;
        this.active=active;
        this.coreSize = coreSize;
        this.maxSize = maxSize;
        init();
    }

    /**
     * 首先创建最小数量的线程池
     * 初始化线程池
     */
    @Override
    public void init() {
        for (int i = 0; i < coreSize; i++) {
            createWorkTask();
        }
        this.size = coreSize;
        this.start();
       // System.out.println("初始化完毕");
    }

    /**
     * 创建空闲线程 加入线程池
     */
    @Override
    public void createWorkTask() {
        WorkerTask task = new WorkerTask("pool_" + (seq++));
        task.start();
        THREAD_QUEUE.add(task);//添加线程
    }

    @Override
    public void submit(Runnable runnable) {
        //如果不加锁，有可能被其他线程抢到控制权执行了对应方法
        synchronized (TASK_QUEUE) {
            if (TASK_QUEUE.size() > queueSize) {
                discardPolicy.discard();
            }
            //System.out.println("添加任务");

            //因为LinkedList是链表结构，所以每添加一个元素就是让这个元素链接到链表的尾部
            TASK_QUEUE.addLast(runnable);//添加线程到列表的结束位置
            TASK_QUEUE.notifyAll();//唤醒所有线程
       }
    }

    /**
     * 监控线程池，进行动态的扩容和缩小
     */
    @Override
    public void run() {
       // 保证在线程a将其修改为true时，线程b可以立刻得知
        while (!destroyed) {
            try {
                TimeUnit.MILLISECONDS.sleep(1000);
                System.err.println(toString());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(5000);
                //判断当队列大小大于活动大小 创建任务（第一次扩容）
                if (TASK_QUEUE.size() > active && size < active) {
                    for (int i = size; i < active; i++) {
                        createWorkTask();
                    }
                    size = active;
                    System.err.println("第一次扩容为  " + active);
                    //判断当线程任务大小大于最大线程大小时，创建任务（创建任务）
                } else if (TASK_QUEUE.size() > maxSize && size < maxSize) {
                    for (int i = size; i < maxSize; i++) {
                        createWorkTask();
                    }
                    System.err.println("第二次扩容为  " + maxSize);
                    size = maxSize;
                }
                //回收
                    if (TASK_QUEUE.isEmpty() && size > coreSize) {
                        int release = size - coreSize;
                        //循环遍历Iterator对象
                        for (Iterator<WorkerTask> it = THREAD_QUEUE.iterator(); it.hasNext(); ) {//判断容器中是否还有线程
                            if (release < 0) {
                                break;
                            }
                            WorkerTask task = it.next();//获得容器中的下一个线程
                            task.interrupt();//阻断状态
                            it.remove();//返回的线程并删除
                            release--;
                        }
                        size = coreSize;

                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }


    /**
     * 任务有四种状态
     */
    /*private enum TaskState {
        FREE, RUNNING, BLOCKED, DEAD
    }*/

    /**
     * 内部类 将runnable封装为task执行
     */
    private static class WorkerTask extends Thread {
        private  State taskState;// 状态

        public WorkerTask(String name) {
            super(name);
        }

        /*public TaskState getTaskState() {
            return taskState;
        }*/

        /**
         * 运行任务队列中的任务
         */
        @Override
        public void run() {
            //循环标记
            OUTER:
            while (this.taskState != State.TERMINATED) {//终止线程的读取状态
                Runnable runnable;
                //在多线程访问的时候，同一时刻只能有一个线程能够用
                //如果不加锁，有可能被其他线程抢到控制权执行了对应方法
                synchronized (TASK_QUEUE) {
                    while (TASK_QUEUE.isEmpty()) {
                        try {
                            taskState = State.WAITING;//等待
                            TASK_QUEUE.wait();
                        } catch (InterruptedException e) {
                            break OUTER;
                        }
                    }
                    runnable = TASK_QUEUE.removeFirst();
                }
                if (runnable != null) {
                    taskState = State.RUNNABLE;//就绪运行
                    runnable.run();
                    taskState = State.NEW;//新建
                }
            }
        }
    }

}