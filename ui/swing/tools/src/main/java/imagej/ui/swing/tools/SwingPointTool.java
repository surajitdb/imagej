/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2012 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package imagej.ui.swing.tools;

import java.awt.Shape;

import imagej.data.display.ImageDisplay;
import imagej.data.display.OverlayView;
import imagej.data.overlay.Overlay;
import imagej.data.overlay.PointOverlay;
import imagej.input.MouseCursor;
import imagej.plugin.Plugin;
import imagej.ui.swing.overlay.AbstractJHotDrawAdapter;
import imagej.ui.swing.overlay.IJCreationTool;
import imagej.ui.swing.overlay.JHotDrawAdapter;
import imagej.ui.swing.overlay.JHotDrawTool;
import imagej.ui.swing.overlay.SwingPointFigure;
import imagej.ui.swing.tools.overlay.SwingAngleTool;
import imagej.util.ColorRGB;

import org.jhotdraw.draw.Figure;

/**
 * Swing/JHotDraw implementation of point tool.
 * 
 * @author Barry DeZonia
 */
@Plugin(type = JHotDrawAdapter.class, name = "Point",
	description = "Point overlays", iconPath = "/icons/tools/point.png",
	priority = SwingPointTool.PRIORITY, enabled = true)
public class SwingPointTool extends AbstractJHotDrawAdapter<PointOverlay, SwingPointFigure> {

	public static final double PRIORITY = SwingAngleTool.PRIORITY - 1;

	// -- JHotDrawAdapter methods --

	@Override
	public boolean supports(final Overlay overlay, final Figure figure) {
		if (!(overlay instanceof PointOverlay)) return false;
		return figure == null || figure instanceof SwingPointFigure;
	}

	@Override
	public PointOverlay createNewOverlay() {
		return new PointOverlay(getContext());
	}

	@Override
	public Figure createDefaultFigure() {
		final SwingPointFigure figure = new SwingPointFigure();
		initDefaultSettings(figure);
		return figure;
	}

	@Override
	public void updateFigure(final OverlayView view, final SwingPointFigure figure) {
		super.updateFigure(view, figure);
		assert figure instanceof SwingPointFigure;
		final SwingPointFigure pointFigure = (SwingPointFigure) figure;
		final Overlay overlay = view.getData();
		assert overlay instanceof PointOverlay;
		final PointOverlay pointOverlay = (PointOverlay) overlay;
		pointFigure.setFillColor(pointOverlay.getFillColor());
		pointFigure.setLineColor(pointOverlay.getLineColor());
		pointFigure.setPoint(pointOverlay.getPoint(0), pointOverlay.getPoint(1));
	}

	@Override
	public void updateOverlay(final SwingPointFigure figure, final OverlayView view) {
		assert figure instanceof SwingPointFigure;
		final SwingPointFigure point = (SwingPointFigure) figure;
		final Overlay overlay = view.getData();
		assert overlay instanceof PointOverlay;
		final PointOverlay pointOverlay = (PointOverlay) overlay;
		// do not let call to super.updateOverlay() mess with drawing attributes
		// so save colors
		final ColorRGB fillColor = overlay.getFillColor();
		final ColorRGB lineColor = overlay.getLineColor();
		// call super in case it initializes anything of importance
		super.updateOverlay(figure, view);
		// and restore colors to what we really want
		overlay.setFillColor(fillColor);
		overlay.setLineColor(lineColor);
		// set location
		final double x = point.getX();
		final double y = point.getY();
		pointOverlay.setPoint(x, 0);
		pointOverlay.setPoint(y, 1);
		overlay.update();
		reportPoint(x, y);
	}

	@Override
	public MouseCursor getCursor() {
		return MouseCursor.CROSSHAIR;
	}

	@Override
	public JHotDrawTool getCreationTool(final ImageDisplay display) {
		return new IJCreationTool<SwingPointFigure>(display, this);
	}

	@Override
	public Shape toShape(final SwingPointFigure figure) {
		throw new UnsupportedOperationException();
	}

}