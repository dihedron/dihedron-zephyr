/**
 * Copyright (c) 2014, Andrea Funto'. All rights reserved.
 *
 * This file is part of the Zephyr framework ("Zephyr").
 *
 * Zephyr is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free 
 * Software Foundation, either version 3 of the License, or (at your option) 
 * any later version.
 *
 * Zephyr is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR 
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more 
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License 
 * along with Zephyr. If not, see <http://www.gnu.org/licenses/>.
 */

package org.dihedron.zephyr;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dihedron.zephyr.exceptions.ZephyrException;
import org.dihedron.zephyr.interceptors.Interceptor;
import org.dihedron.zephyr.interceptors.InterceptorStack;
import org.dihedron.zephyr.targets.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrea Funto'
 */
public class ActionInvocation {
	
	/**
	 * The logger.
	 */
	private static final Logger logger = LoggerFactory.getLogger(ActionInvocation.class);
	
	/**
	 * The information (metadata) pertaining to the business method being invoked 
	 * on the action instance.
	 */
	private Target target;
	
	/**
	 * The actual action instance on which the business method is being invoked.
	 */
	private Object action;
	
	/**
	 * The <code>ActionRequest</code>, <code>EventRequest</code> or
	 * <code>RenderRequest</code> object.
	 */
	private HttpServletRequest request;
	
	/**
	 * The <code>ActionResponse</code>, <code>EventResponse</code> or
	 * <code>RenderResponse</code> object.
	 */
	private HttpServletResponse response;

	/**
	 * The thread-specific store for the iterator on the list of interceptors.
	 */
	private ThreadLocal<Iterator<Interceptor>> iterator = new ThreadLocal<Iterator<Interceptor>>() {
		@Override protected Iterator<Interceptor> initialValue() {
			return null;
		}
	};
	
	/**
	 * The stack of interceptors.
	 */
	private InterceptorStack interceptors;
	
	/**
	 * Constructor.
	 * 
	 * @param target
	 *   the metadata (information) about the method being invoked.
	 * @param action
	 *   the instance of action object on which the business method exists; this 
	 *   can be manipulated and used, e.g. to inject resources in a DI interceptor.
	 * @param interceptors
	 *   the {@code InterceptorStack} representing the set of interceptors 
	 * @param request
	 *   the {@code HttpServletRequest} object.
	 * @param response
	 *   the {@code HttpServletResponse} object.
	 */
	public ActionInvocation(Target target, Object action, InterceptorStack interceptors, HttpServletRequest request, HttpServletResponse response) {
		this.target = target;
		this.action = action;
		this.request = request;
		this.response = response;
		this.interceptors = interceptors;
		this.iterator.set(null);
	}
	
	/**
	 * Returns the information pertaining to the method being invoked.
	 * 
	 * @return
	 *   the information on the business method being invoked.
	 */
	public Target getTarget() {
		return target;
	}
	
	/**
	 * Returns the instance of action object (as returned by the target's
	 * factory method) on which the businedss method being invoked resides.
	 * This object could be a brand-new instance at each invocation or the
	 * same object instance userd over and over again if the object is 
	 * stateless (that is, it has no per-instance field).
	 * 
	 * @return
	 *   the action object.
	 */
	public Object getAction() {
		return action;
	}
	
	/**
	 * Returns the current servlet request.
	 * 
	 * @return
	 *   the current servlet request.
	 */
	public HttpServletRequest getPortletRequest() {
		return request;
	}
	
	/**
	 * Returns the current servlet response.
	 * 
	 * @return
	 *   the current servlet response.
	 */
	public HttpServletResponse getPortletResponse() {
		return response;
	}
	
	/**
	 * Invokes the next interceptor in the stack, or the action if this
	 * is the last interceptor.
	 * 
	 * @return
	 *   the interceptor result; if the interceptor is not intended to divert 
	 *   control flow, it should pass through whatever results from the nested 
	 *   interceptor call; changing this result with a different value results 
	 *   in a deviation of the workflow.  
	 * @throws StrutletsException
	 */
	public String invoke() throws ZephyrException {
		
		// invoke the interceptors stack
		if(iterator.get() == null) {
			iterator.set(interceptors.iterator());
		}
		if(iterator.get().hasNext()) {
			return iterator.get().next().intercept(this);
		}
		// now invoke the static proxy method 
		try {
			Method proxy = target.getStubMethod();
			logger.trace("invoking actual method on action instance through proxy '{}'", proxy.getName());
			return (String)proxy.invoke(null, /*action*/null);
		} catch (IllegalArgumentException e) {
			logger.error("illegal argument to proxy method invocation", e);
			throw new ZephyrException("illegal argument to proxy method invocation", e);
		} catch (IllegalAccessException e) {
			logger.error("illegal access to proxy method during invocation", e);
			throw new ZephyrException("illegal access to proxy method during invocation", e);
		} catch (InvocationTargetException e) {
			logger.error("invocation target error calling proxy method", e);
			throw new ZephyrException("invocation target error calling proxy method", e);
		}
	}
	
	/**
	 * Cleans up after the invocation has completed, by unbinding data from the 
	 * thread-local storage; this method must be called after each invocation,
	 * no matter how it ends, whether in success or with an exception; add it to 
	 * a "finally" block around the action invocation.
	 */
	public void cleanup() {
		logger.debug("removing the interceptors iterator from the thread-local storage");
		iterator.remove();
	}
}