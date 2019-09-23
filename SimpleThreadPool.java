package com.test4;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;


/**
 * @Author:pjq
 * @Date: 2019/9/23 9:30
 */
public class SimpleThreadPool extends Thread {


    /**
     * 当前线程队列的大小
     */
    private int size;
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
     * 线程ID
     */
    private static volatile int seq = 0;
    /**
     * 线程池是否被销毁
     */
    private volatile boolean destroyed = false;

    private int coreSize;
    private int maxSize;
    private int active;


    private  DiscardPolicy discardPolicy = new MyAbortPolicy();

    class MyAbortPolicy implements DiscardPolicy {

        @Override
        public void discard(String name) throws RuntimeException {
            throw new RuntimeException("超出任务...") ;

        }
    }

    interface DiscardPolicy {
        void discard(String name) throws RuntimeException;
    }


    public SimpleThreadPool() {
        this(4, 8, 12, TASK_QUEUE_SIZE);
    }


    public SimpleThreadPool(int coreSize, int active, int maxSize, int queue_size) {
        this.queueSize = queue_size;
        this.coreSize = coreSize;
        this.maxSize = maxSize;
        this.active = active;
        init();
    }

    /**
     * 首先创建最小数量的线程池
     */
    private void init() {
        for (int i = 0; i < coreSize; i++) {
            createWorkTask();
        }
        this.size = coreSize;
        this.start();
    }

    /**
     * 创建空闲线程 加入线程池
     */
    private void createWorkTask() {
        WorkerTask task = new WorkerTask("pool_" + (seq++));
        task.start();
        THREAD_QUEUE.add(task);//添加任务

    }

    /**
     * 向外提供方法  提交任务,如果任务大小超过线程池大小 200 则直接抛弃
     */
    public void submit(Runnable runnable) {
        synchronized (TASK_QUEUE) {
            if (TASK_QUEUE.size() > queueSize) {
                discardPolicy.discard("抛弃任务。。");
            }
            TASK_QUEUE.addLast(runnable);
            TASK_QUEUE.notifyAll();
        }
    }

    public void shutDown() throws InterruptedException {
        while (!TASK_QUEUE.isEmpty()) {
            Thread.sleep(1000);
        }
        synchronized (THREAD_QUEUE) {
            int initVal = THREAD_QUEUE.size();
            while (initVal > 0) {
                for (WorkerTask workerTask : THREAD_QUEUE) {
                    if (workerTask.taskState == TaskState.BLOCKED) {
                        workerTask.interrupt();
                        initVal--;
                    } else {
                        Thread.sleep(1000);
                    }
                }
            }
        }
        this.destroyed = true;
        System.out.println("Thread pool shutDown");
        System.exit(0);
    }
    /**
     * 监控线程池，进行动态的扩容和缩小
     */
    @Override
    public void run() {
        while (!destroyed) {
            try {
                TimeUnit.MILLISECONDS.sleep(1000);
                System.err.printf("pool Min:%d,Active:%d,Max:%d,current:%d,QueueSize:%d\n", coreSize, active, maxSize, size, TASK_QUEUE.size());

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
                    System.out.println("active 池自动调整为  " + active);
                    size = active;
                    //判断当线程任务大小大于最大线程大小时，创建任务（创建任务）
                } else if (TASK_QUEUE.size() > maxSize && size < maxSize) {
                    for (int i = size; i < maxSize; i++) {
                        createWorkTask();
                    }
                    System.out.println("max 池自动调整为  " + maxSize);
                    size = maxSize;
                }
                synchronized (THREAD_QUEUE) {
                    if (TASK_QUEUE.isEmpty() && size > active) {
                        int release = size - active;
                        for (Iterator<WorkerTask> it = THREAD_QUEUE.iterator(); it.hasNext(); ) {
                            if (release < 0) {
                                break;
                            }
                            WorkerTask task = it.next();
                            task.interrupt();
                            it.remove();
                            release--;
                        }
                        size = active;
                        System.out.println("池释放大小为" + size);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 任务有四种状态
     */
    private enum TaskState {
        FREE, RUNNING, BLOCKED, DEAD
    }

    /**
     * 内部类 将runnable封装为task执行
     */
    private static class WorkerTask extends Thread {
        private volatile TaskState taskState = TaskState.FREE;

        public WorkerTask(String name) {
            super(name);
        }

        public TaskState getTaskState() {
            return taskState;
        }

        /**
         * 运行任务队列中的任务
         */
        @Override
        public void run() {
            //循环标记
            OUTER:
            while (this.taskState != TaskState.DEAD) {
                Runnable runnable;
                synchronized (TASK_QUEUE) {
                    while (TASK_QUEUE.isEmpty()) {
                        try {
                            taskState = TaskState.BLOCKED;
                            TASK_QUEUE.wait();
                        } catch (InterruptedException e) {
                            break OUTER;
                        }

                    }
                    runnable = TASK_QUEUE.removeFirst();
                }
                if (runnable != null) {
                    taskState = TaskState.RUNNING;
                    runnable.run();
                    taskState = TaskState.FREE;
                }
            }
        }


    }


    public static void main(String[] args) throws InterruptedException {
        SimpleThreadPool threadPool = new SimpleThreadPool();
        IntStream.range(0, 200).forEach(i -> threadPool.submit(() -> {
            System.out.println("任务" + Thread.currentThread().getName() + "  接收... " + i);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("任务" + Thread.currentThread().getName() + "  关闭... " + i);

        }));
threadPool.shutDown();


      /* for ( int i = 0; i < 203; i++) {

          threadPool.submit(()-> {

                System.out.println("运行的任务池" + Thread.currentThread().getName()+"  ");

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                System.out.println("运行任务" + Thread.currentThread().getName() + "关");
            });
           // System.out.println("----------------------------");
        }
*/

    }
}