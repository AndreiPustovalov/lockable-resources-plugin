/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (c) 2013-2015, 6WIND S.A.                                 *
 *                          SAP SE                                     *
 *                                                                     *
 * This file is part of the Jenkins Lockable Resources Plugin and is   *
 * published under the MIT license.                                    *
 *                                                                     *
 * See the "LICENSE.txt" file for more information.                    *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package org.jenkins.plugins.lockableresources;

import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import static org.jenkins.plugins.lockableresources.Constants.*;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class RequiredResourcesProperty extends JobProperty<Job<?, ?>> {

	public final List<Resource> resources;
	public final String resourceNamesVar;

	// maintained to facilitate upgrade from v1.6
	@Deprecated
	private final transient String labelName = null;

	@DataBoundConstructor
	public RequiredResourcesProperty(List<Resource> resources,
			String resourceNamesVar) {
		super();
		this.resourceNamesVar = resourceNamesVar;
		this.resources = new ArrayList<>();
		if (resources != null)
			this.resources.addAll(resources);
	}

	public String getResourceNames() {
		return resources.get(0).resourceNames;
	}

//	public String getResourceNamesVar() {
//		return resourceNamesVar;
//	}

//	public String getResourceNumber() {
//		return resources.get(0).resourceNumber;
//	}

	public static class Resource extends AbstractDescribableImpl<Resource> {
		public final String resourceNames;
		public final String resourceNumber;

		@DataBoundConstructor
		public Resource(String resourceNames, String resourceNumber) {
			this.resourceNames = resourceNames;
			this.resourceNumber = resourceNumber;
		}

		@Extension
		public static class DescriptorImpl extends Descriptor<Resource> {
			public String getDisplayName() { return ""; }
		}
	}

	@Extension
	public static class DescriptorImpl extends JobPropertyDescriptor {

		@Override
		public String getDisplayName() {
			return "Required Lockable Resources";
		}

//		@Override
//		public RequiredResourcesProperty newInstance(StaplerRequest req,
//				JSONObject formData) throws FormException {
//
//			if (formData.isNullObject())
//				return null;
//
//			JSONObject json = formData
//					.getJSONObject("required-lockable-resources");
//			if (json.isNullObject())
//				return null;
//
//			String resourceNames = Util.fixEmptyAndTrim(json
//					.getString("resourceNames"));
//
//			String resourceNamesVar = Util.fixEmptyAndTrim(json
//					.getString("resourceNamesVar"));
//
//			String resourceNumber = Util.fixEmptyAndTrim(json
//					.getString("resourceNumber"));
//
//			if (resourceNames == null )
//				return null;
//
//			return new RequiredResourcesProperty(resourceNames,
//					resourceNamesVar, resourceNumber);
//		}

		public FormValidation doCheckResourceNames(@QueryParameter String value) {
			String names = Util.fixEmptyAndTrim(value);
			if (names == null) {
				return FormValidation.ok();
			} else {
				List<String> wrongNames = new ArrayList<String>();
				for (String name : names.split(RESOURCES_SPLIT_REGEX)) {
					boolean found = false;
					for (LockableResource r : LockableResourcesManager.get()
							.getResources()) {
						if (r.getName().equals(name)) {
							found = true;
							break;
						}
					}
					if (!found)
						wrongNames.add(name);
				}
				// now filter out valid labels
				Iterator<String> it = wrongNames.iterator();
				while ( it.hasNext() ) {
					String label = it.next();
					if (LockableResourcesManager.get().isValidLabel(label)) {
						it.remove();
					}
				}
				if (wrongNames.isEmpty()) {
					return FormValidation.ok();
				} else {
					return FormValidation
							.error("The following resources do not exist: "
									+ wrongNames);
				}
			}
		}

		public FormValidation doCheckResourceNumber(@QueryParameter String value,
				@QueryParameter String resourceNames) {

			String number = Util.fixEmptyAndTrim(value);
			String names = Util.fixEmptyAndTrim(resourceNames);

			if (number == null || number.equals("") || number.trim().equals("0")) {
				return FormValidation.ok();
			}

			int numAsInt;
			try {
				numAsInt = Integer.parseInt(number);
			} catch(NumberFormatException e)  {
				return FormValidation.error(
					"Could not parse the given value as integer.");
			}

			LockableResourcesManager manager = LockableResourcesManager.get();

			int numResources = 0;
			if (names != null) {
				if ( names.startsWith(Constants.GROOVY_LABEL_MARKER) ) {
					numResources = Integer.MAX_VALUE;
				}
				else {
					HashSet<String> resources = new HashSet<String>();
					resources.addAll(Arrays.asList(names.split(RESOURCES_SPLIT_REGEX)));
					Iterator<String> it = resources.iterator();
					HashSet<String> labelResources = new HashSet<String>();
					while ( it.hasNext() ) {
						String resource = it.next();
						if ( manager.fromName(resource) == null ) {
							it.remove();
							for ( LockableResource r : manager.getResourcesWithLabel(resource) ) {
								labelResources.add(r.getName());
							}
						}
					}
					resources.addAll(labelResources);
					numResources = resources.size();
				}
			}

			if (numResources < numAsInt) {
				return FormValidation.error(String.format(
					"Given amount %d is greater than amount of resources: %d.",
					numAsInt,
					numResources));
			}
			return FormValidation.ok();
		}

		public AutoCompletionCandidates doAutoCompleteResourceNames(
				@QueryParameter String value) {
			AutoCompletionCandidates c = new AutoCompletionCandidates();

			value = Util.fixEmptyAndTrim(value);

			if (value != null) {
				for (LockableResource r : LockableResourcesManager.get()
						.getResources()) {
					if (r.getName().startsWith(value))
						c.add(r.getName());
				}
				for (String l : LockableResourcesManager.get().getAllLabels()) {
					if ( l.startsWith(value) ) c.add(l);
				}
			}

			return c;
		}
	}
}

