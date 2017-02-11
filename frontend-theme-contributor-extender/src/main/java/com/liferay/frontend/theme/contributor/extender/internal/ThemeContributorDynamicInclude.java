/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.frontend.theme.contributor.extender.internal;

import com.liferay.frontend.theme.contributor.extender.BundleWebResources;
import com.liferay.portal.kernel.servlet.PortalWebResourceConstants;
import com.liferay.portal.kernel.servlet.PortalWebResourcesUtil;
import com.liferay.portal.kernel.servlet.taglib.DynamicInclude;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.WebKeys;

import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

/**
 * @author Carlos Sierra Andrés
 */
@Component(immediate = true)
public class ThemeContributorDynamicInclude implements DynamicInclude {

	@Override
	public void include(
			HttpServletRequest request, HttpServletResponse response,
			String key)
		throws IOException {

		ThemeDisplay themeDisplay = (ThemeDisplay)request.getAttribute(
			WebKeys.THEME_DISPLAY);

		long themeLastModified = PortalWebResourcesUtil.getLastModified(
			PortalWebResourceConstants.RESOURCE_TYPE_THEME_CONTRIBUTOR);

		LinkRenderer linkRenderer = new LinkRenderer() {

			@Override
			public void render(PrintWriter printWriter, String href) {
				printWriter.println(
					"<link data-senna-track=\"temporary\" href=\"" + href +
						"\" rel=\"stylesheet\" type = \"text/css\" />");
			}

		};

		if (themeDisplay.isThemeCssFastLoad()) {
			_renderCombo(
				"css", themeLastModified, request, response.getWriter(),
				_cssResourceURLs, linkRenderer);
		}
		else {
			_renderSimple(
				themeLastModified, request, response.getWriter(),
				_cssResourceURLs, linkRenderer);
		}

		linkRenderer = new LinkRenderer() {

			@Override
			public void render(PrintWriter printWriter, String href) {
				printWriter.println(
					"<script data-senna-track=\"temporary\" src=\"" + href +
						"\" \" type = \"text/javascript\"></script>");
			}

		};

		if (themeDisplay.isThemeJsFastLoad()) {
			_renderCombo(
				"js", themeLastModified, request, response.getWriter(),
				_jsResourceURLs, linkRenderer);
		}
		else {
			_renderSimple(
				themeLastModified, request, response.getWriter(),
				_jsResourceURLs, linkRenderer);
		}
	}

	@Override
	public void register(DynamicIncludeRegistry dynamicIncludeRegistry) {
		dynamicIncludeRegistry.register(
			"/html/common/themes/top_head.jsp#post");
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_bundleContext = bundleContext;
	}

	@Reference(
		cardinality = ReferenceCardinality.MULTIPLE,
		policy = ReferencePolicy.DYNAMIC, unbind = "removeBundleWebResources"
	)
	protected void addBundleWebResources(
		ServiceReference<BundleWebResources>
			bundleWebResourcesServiceReference) {

		synchronized (_bundleWebResourcesServiceReferences) {
			_bundleWebResourcesServiceReferences.add(
				bundleWebResourcesServiceReference);

			_rebuild();
		}
	}

	protected void removeBundleWebResources(
		ServiceReference<BundleWebResources>
			bundleWebResourcesServiceReference) {

		synchronized (_bundleWebResourcesServiceReferences) {
			_bundleWebResourcesServiceReferences.remove(
				bundleWebResourcesServiceReference);

			_rebuild();
		}
	}

	private void _rebuild() {
		Collection<String> cssResourceURLs = new ArrayList<>();
		Collection<String> jsResourceURLs = new ArrayList<>();

		for (ServiceReference<BundleWebResources>
				bundleWebResourcesServiceReference :
					_bundleWebResourcesServiceReferences) {

			BundleWebResources bundleWebResources = _bundleContext.getService(
				bundleWebResourcesServiceReference);

			try {
				String servletContextPath =
					bundleWebResources.getServletContextPath();

				for (String cssResourcePath :
						bundleWebResources.getCssResourcePaths()) {

					cssResourceURLs.add(servletContextPath + cssResourcePath);
				}

				for (String jsResourcePath :
						bundleWebResources.getJsResourcePaths()) {

					jsResourceURLs.add(servletContextPath + jsResourcePath);
				}
			}
			finally {
				_bundleContext.ungetService(bundleWebResourcesServiceReference);
			}
		}

		_cssResourceURLs = cssResourceURLs;
		_jsResourceURLs = jsResourceURLs;
	}

	private void _renderCombo(
		String minifierType, long themeLastModified, HttpServletRequest request,
		PrintWriter printWriter, Collection<String> resourceURLs,
		LinkRenderer linkRenderer) {

		if (resourceURLs.isEmpty()) {
			return;
		}

		StringBundler sb = new StringBundler();

		sb.append(
			PortalUtil.getStaticResourceURL(
				request, PortalUtil.getPathContext() + "/combo",
				"minifierType=" + minifierType, themeLastModified));

		for (String resourceURL : resourceURLs) {
			sb.append("&");
			sb.append(resourceURL);
		}

		linkRenderer.render(printWriter, sb.toString());
	}

	private void _renderSimple(
		long themeLastModified, HttpServletRequest request,
		PrintWriter printWriter, Collection<String> resourceURLs,
		LinkRenderer linkRenderer) {

		ThemeDisplay themeDisplay = (ThemeDisplay)request.getAttribute(
			WebKeys.THEME_DISPLAY);

		for (String resourceURL : resourceURLs) {
			String staticResourceURL = PortalUtil.getStaticResourceURL(
				request,
				themeDisplay.getPortalURL() + PortalUtil.getPathProxy() +
					resourceURL,
				themeLastModified);

			linkRenderer.render(printWriter, staticResourceURL);
		}
	}

	private BundleContext _bundleContext;
	private final Collection<ServiceReference<BundleWebResources>>
		_bundleWebResourcesServiceReferences = new TreeSet<>();
	private volatile Collection<String> _cssResourceURLs =
		Collections.emptyList();
	private volatile Collection<String> _jsResourceURLs =
		Collections.emptyList();

	private interface LinkRenderer {

		public void render(PrintWriter printWriter, String href);

	}

}