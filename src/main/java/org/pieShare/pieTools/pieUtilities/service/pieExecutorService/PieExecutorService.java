/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pieShare.pieTools.pieUtilities.service.pieExecutorService;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.Validate;
import org.pieShare.pieTools.pieUtilities.service.pieExecutorService.api.IExecutorService;
import org.pieShare.pieTools.pieUtilities.service.pieExecutorService.api.IPieExecutorTaskFactory;
import org.pieShare.pieTools.pieUtilities.service.pieExecutorService.api.event.IPieEvent;
import org.pieShare.pieTools.pieUtilities.service.pieExecutorService.api.task.IPieEventTask;
import org.pieShare.pieTools.pieUtilities.service.pieExecutorService.api.task.IPieTask;
import org.pieShare.pieTools.pieUtilities.service.pieExecutorService.exception.PieExecutorTaskFactoryException;
import org.pieShare.pieTools.pieUtilities.service.pieLogger.PieLogger;
import org.pieShare.pieTools.pieUtilities.service.shutDownService.api.IShutdownService;
import org.pieShare.pieTools.pieUtilities.service.shutDownService.api.IShutdownableService;

/**
 *
 * @author Svetoslav
 */
//todo: read up why extending was the better move and document; check also git comments for hints
public class PieExecutorService extends ThreadPoolExecutor implements IExecutorService, IShutdownableService {

	//todo-sv: rethink the whole derive from ThreadPoolExecutor instead of just using one
	public static PieExecutorService newCachedPieExecutorService() {
		//todo-sv: further think about good tradeoff between concurrency
		//and to much threads (especially on android)
		//for the time being that is a quite good value 
		//considering our max parallel bitTorrent connections
		PieExecutorService service = new PieExecutorService(10, 10, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		service.allowCoreThreadTimeOut(true);
		return service;
	}

	private IPieExecutorTaskFactory executorFactory;

	public PieExecutorService(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
	}

	public PieExecutorService(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
	}

	public PieExecutorService(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
	}

	public PieExecutorService(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
	}

	/*public void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}*/
	public void setExecutorFactory(IPieExecutorTaskFactory executorFactory) {
		this.executorFactory = executorFactory;
	}

	@Override
	public void execute(IPieTask task) {
		Validate.notNull(task);
		try {
			if (!this.isShutdown()) {
				PieLogger.trace(this.getClass(), "Executing {}", task);
				super.execute(task);
				//this.executor.execute(task);
			}
		} catch (NullPointerException ex) {
			PieLogger.info(this.getClass(), "Exception in PieExecutorService!", ex);
		} catch (RejectedExecutionException ex) {
			if (this.isShutdown()) {
				PieLogger.trace(this.getClass(), "Rejected task {} due to shutdown!", task.getClass().toString());
			} else {
				PieLogger.warn(this.getClass(), "Executor rejected task!", ex);
			}
		}
	}

	@Override
	public void handlePieEvent(IPieEvent event) throws PieExecutorTaskFactoryException {
		IPieEventTask task = this.executorFactory.getTask(event);
		this.execute(task);
	}

	@Override
	public void shutdown() {
		super.shutdown();
		//this.executor.shutdown();
	}

	@Override
	protected void afterExecute(Runnable r, Throwable t) {
		super.afterExecute(r, t);

		if (t != null) {
			PieLogger.error(this.getClass(), "Error in task!", t);
		}

		//todo-sv: why?... just why?
		if (r instanceof Future<?>) {
			try {
				Future<?> future = (Future<?>) r;
				future.get();
			} catch (InterruptedException ex) {
				PieLogger.error(this.getClass(), "Task interrupted!", ex);
			} catch (ExecutionException ex) {
				PieLogger.error(this.getClass(), "Error in task!", ex);
			} catch (CancellationException ex) {
				PieLogger.error(this.getClass(), "Task canceled!", ex);
			}
		}
	}

	@Override
	public void setShutdownService(IShutdownService service) {
		service.registerListener(this);
	}
}
