//
// ModuleService.java
//

/*
ImageJ software for multidimensional image processing and analysis.

Copyright (c) 2010, ImageJDev.org.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the names of the ImageJDev.org developers nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/

package imagej.ext.module;

import imagej.AbstractService;
import imagej.ImageJ;
import imagej.Service;
import imagej.event.EventService;
import imagej.ext.Accelerator;
import imagej.ext.MenuPath;
import imagej.ext.module.event.ModulesAddedEvent;
import imagej.ext.module.event.ModulesRemovedEvent;
import imagej.ext.module.process.ModulePostprocessor;
import imagej.ext.module.process.ModulePreprocessor;
import imagej.thread.ThreadService;
import imagej.util.ClassUtils;
import imagej.util.Log;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Service for keeping track of and executing available modules.
 * 
 * @author Curtis Rueden
 * @see Module
 * @see ModuleInfo
 */
@Service
public class ModuleService extends AbstractService {

	private final EventService eventService;
	private final ThreadService threadService;

	/** Index of registered modules. */
	private final ModuleIndex moduleIndex = new ModuleIndex();

	// -- Constructors --

	public ModuleService() {
		// NB: Required by SezPoz.
		super(null);
		throw new UnsupportedOperationException();
	}

	public ModuleService(final ImageJ context, final EventService eventService,
		final ThreadService threadService)
	{
		super(context);
		this.eventService = eventService;
		this.threadService = threadService;
	}

	// -- ModuleService methods --

	/** Gets the index of available modules. */
	public ModuleIndex getIndex() {
		return moduleIndex;
	}

	/** Manually registers a module with the module service. */
	public void addModule(final ModuleInfo module) {
		moduleIndex.add(module);
		eventService.publish(new ModulesAddedEvent(module));
	}

	/** Manually unregisters a module with the module service. */
	public void removeModule(final ModuleInfo module) {
		moduleIndex.remove(module);
		eventService.publish(new ModulesRemovedEvent(module));
	}

	/** Manually registers a list of modules with the module service. */
	public void addModules(final Collection<? extends ModuleInfo> modules) {
		moduleIndex.addAll(modules);
		eventService.publish(new ModulesAddedEvent(modules));
	}

	/** Manually unregisters a list of modules with the module service. */
	public void removeModules(final Collection<? extends ModuleInfo> modules) {
		moduleIndex.removeAll(modules);
		eventService.publish(new ModulesRemovedEvent(modules));
	}

	/** Gets the list of available modules. */
	public List<ModuleInfo> getModules() {
		return moduleIndex.getAll();
	}

	/**
	 * Gets the module for a given keyboard shortcut.
	 * 
	 * @param acc the accelerator for which to search.
	 * @return the module info for the corresponding module, or null.
	 */
	public ModuleInfo getModuleForAccelerator(final Accelerator acc) {
		for (final ModuleInfo info : getModules()) {
			final MenuPath menuPath = info.getMenuPath();
			if (menuPath == null || menuPath.isEmpty()) continue;
			if (acc.equals(menuPath.getLeaf().getAccelerator())) return info;
		}
		return null;
	}

	/**
	 * Executes the given module, without any pre- or postprocessing.
	 * 
	 * @param info The module to instantiate and run.
	 * @param inputValues List of input parameter values, in the same order
	 *          declared by the {@link ModuleInfo}. Passing a number of values
	 *          that differs from the number of input parameters is allowed, but
	 *          will issue a warning. Passing a value of a type incompatible with
	 *          the associated input parameter will issue an error and ignore that
	 *          value.
	 * @return {@link Future} of the module instance being executed. Calling
	 *         {@link Future#get()} will block until execution is complete.
	 */
	public Future<Module>
		run(final ModuleInfo info, final Object... inputValues)
	{
		return run(info, null, null, inputValues);
	}

	/**
	 * Executes the given module.
	 * 
	 * @param info The module to instantiate and run.
	 * @param pre List of preprocessing steps to perform.
	 * @param post List of postprocessing steps to perform.
	 * @param inputValues List of input parameter values, in the same order
	 *          declared by the {@link ModuleInfo}. Passing a number of values
	 *          that differs from the number of input parameters is allowed, but
	 *          will issue a warning. Passing a value of a type incompatible with
	 *          the associated input parameter will issue an error and ignore that
	 *          value.
	 * @return {@link Future} of the module instance being executed. Calling
	 *         {@link Future#get()} will block until execution is complete.
	 */
	public Future<Module> run(final ModuleInfo info,
		final List<? extends ModulePreprocessor> pre,
		final List<? extends ModulePostprocessor> post,
		final Object... inputValues)
	{
		return run(info, pre, post, createMap(info, inputValues));
	}

	/**
	 * Executes the given module.
	 * 
	 * @param info The module to instantiate and run.
	 * @param pre List of preprocessing steps to perform.
	 * @param post List of postprocessing steps to perform.
	 * @param inputMap Table of input parameter values, with keys matching the
	 *          {@link ModuleInfo}'s input parameter names. Passing a value of a
	 *          type incompatible with the associated input parameter will issue
	 *          an error and ignore that value.
	 * @return {@link Future} of the module instance being executed. Calling
	 *         {@link Future#get()} will block until execution is complete.
	 */
	public Future<Module> run(final ModuleInfo info,
		final List<? extends ModulePreprocessor> pre,
		final List<? extends ModulePostprocessor> post,
		final Map<String, Object> inputMap)
	{
		try {
			final Module module = info.createModule();
			return run(module, pre, post, inputMap);
		}
		catch (final ModuleException e) {
			Log.error("Could not execute module: " + info, e);
		}
		return null;
	}

	/**
	 * Executes the given module, without any pre- or postprocessing.
	 * 
	 * @param module The module to run.
	 * @param inputValues List of input parameter values, in the same order
	 *          declared by the module's {@link ModuleInfo}. Passing a number of
	 *          values that differs from the number of input parameters is
	 *          allowed, but will issue a warning. Passing a value of a type
	 *          incompatible with the associated input parameter will issue an
	 *          error and ignore that value.
	 * @return {@link Future} of the module instance being executed. Calling
	 *         {@link Future#get()} will block until execution is complete.
	 */
	public Future<Module> run(final Module module, final Object... inputValues) {
		return run(module, null, null, inputValues);
	}

	/**
	 * Executes the given module.
	 * 
	 * @param module The module to run.
	 * @param pre List of preprocessing steps to perform.
	 * @param post List of postprocessing steps to perform.
	 * @param inputValues List of input parameter values, in the same order
	 *          declared by the module's {@link ModuleInfo}. Passing a number of
	 *          values that differs from the number of input parameters is
	 *          allowed, but will issue a warning. Passing a value of a type
	 *          incompatible with the associated input parameter will issue an
	 *          error and ignore that value.
	 * @return {@link Future} of the module instance being executed. Calling
	 *         {@link Future#get()} will block until execution is complete.
	 */
	public Future<Module> run(final Module module,
		final List<? extends ModulePreprocessor> pre,
		final List<? extends ModulePostprocessor> post,
		final Object... inputValues)
	{
		return run(module, pre, post, createMap(module.getInfo(), inputValues));
	}

	/**
	 * Executes the given module.
	 * 
	 * @param module The module to run.
	 * @param pre List of preprocessing steps to perform.
	 * @param post List of postprocessing steps to perform.
	 * @param inputMap Table of input parameter values, with keys matching the
	 *          module's {@link ModuleInfo}'s input parameter names. Passing a
	 *          value of a type incompatible with the associated input parameter
	 *          will issue an error and ignore that value.
	 * @return {@link Future} of the module instance being executed. Calling
	 *         {@link Future#get()} will block until execution is complete.
	 */
	public Future<Module> run(final Module module,
		final List<? extends ModulePreprocessor> pre,
		final List<? extends ModulePostprocessor> post,
		final Map<String, Object> inputMap)
	{
		assignInputs(module, inputMap);
		final ModuleRunner runner = new ModuleRunner(module, pre, post);
		return threadService.run(runner);
	}

	/** Blocks until the given module is finished executing. */
	public Module waitFor(final Future<Module> future) {
		try {
			return future.get();
		}
		catch (final InterruptedException e) {
			Log.error("Module execution interrupted", e);
		}
		catch (final ExecutionException e) {
			Log.error("Error during module execution", e);
		}
		return null;
	}

	/**
	 * Checks the given module for a solitary unresolved input of the given type,
	 * returning the relevant {@link ModuleItem} if found, or null if not exactly
	 * one unresolved input of that type.
	 */
	public <T> ModuleItem<T> getSingleInput(final Module module,
		final Class<T> type)
	{
		return getSingleItem(module, type, module.getInfo().inputs());
	}

	/**
	 * Checks the given module for a solitary unresolved output of the given type,
	 * returning the relevant {@link ModuleItem} if found, or null if not exactly
	 * one unresolved output of that type.
	 */
	public <T> ModuleItem<T> getSingleOutput(final Module module,
		final Class<T> type)
	{
		return getSingleItem(module, type, module.getInfo().outputs());
	}

	// -- Helper methods --

	/**
	 * Converts the given list of values into an input map for use with a module
	 * of the specified {@link ModuleInfo}.
	 */
	private Map<String, Object> createMap(final ModuleInfo info,
		final Object[] values)
	{
		if (values == null || values.length == 0) return null;

		final HashMap<String, Object> inputMap = new HashMap<String, Object>();
		int i = -1;
		for (final ModuleItem<?> input : info.inputs()) {
			i++;
			if (i >= values.length) continue; // no more values
			final String name = input.getName();
			final Object value = values[i];
			inputMap.put(name, value);
		}
		i++;
		if (i != values.length) {
			Log.warn("Argument mismatch: " + values.length + " of " + i +
				" inputs provided:");
		}
		return inputMap;
	}

	/** Sets the given module's input values to those in the given map. */
	private void assignInputs(final Module module,
		final Map<String, Object> inputMap)
	{
		if (inputMap == null) return; // no inputs to assign

		for (final String name : inputMap.keySet()) {
			final ModuleItem<?> input = module.getInfo().getInput(name);
			if (input == null) {
				Log.error("No such input: " + name);
				continue;
			}
			final Object value = inputMap.get(name);
			final Class<?> type = input.getType();
			final Object converted = ClassUtils.convert(value, type);
			if (value != null && converted == null) {
				Log.error("For input " + name + ": incompatible object " +
					value.getClass().getName() + " for type " + type.getName());
				continue;
			}
			module.setInput(name, converted);
			module.setResolved(name, true);
		}
	}

	private <T> ModuleItem<T> getSingleItem(final Module module,
		final Class<T> type, final Iterable<ModuleItem<?>> items)
	{
		ModuleItem<T> result = null;
		for (final ModuleItem<?> item : items) {
			final String name = item.getName();
			final boolean resolved = module.isResolved(name);
			if (resolved) continue; // skip resolved inputs
			if (!type.isAssignableFrom(item.getType())) continue;
			if (result != null) return null; // multiple matching items
			@SuppressWarnings("unchecked")
			final ModuleItem<T> typedItem = (ModuleItem<T>) item;
			result = typedItem;
		}
		return result;
	}

}
