/*
 * {{{ header & license
 * Copyright (c) 2004, 2005 Joshua Marinacci, Torbjoern Gannholm
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package org.xhtmlrenderer.swing;

import org.xhtmlrenderer.extend.FSGlyphVector;
import org.xhtmlrenderer.extend.FontContext;
import org.xhtmlrenderer.extend.OutputDevice;
import org.xhtmlrenderer.extend.TextRenderer;
import org.xhtmlrenderer.render.FSFont;
import org.xhtmlrenderer.render.FSFontMetrics;
import org.xhtmlrenderer.render.JustificationInfo;
import org.xhtmlrenderer.render.LineMetricsAdapter;
import org.xhtmlrenderer.util.Configuration;

import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.AttributedString;
import java.util.Map;


/**
 * Renders to a Graphics2D instance.
 *
 * @author   Joshua Marinacci
 * @author   Torbjoern Gannholm
 */
public class Java2DTextRenderer implements TextRenderer {
    protected float scale;
    protected float threshold;
    protected Object antiAliasRenderingHint;
    protected Object fractionalFontMetricsHint;
    protected ITextRendererFontFallbackStrategy fallbackStrategy;

    public interface ITextRendererFontFallbackStrategy {
		public Font decideFallBackFor(int codePoint, float fontSize);
	}

	/** @noinspection unused*/
    public static class DefaultTextRendererFontFallbackStrategy implements ITextRendererFontFallbackStrategy {
		protected Font fallbackFont = Font.decode("Default");

		public Font decideFallBackFor(int codePoint, float fontSize) {
																		   return fallbackFont.deriveFont(fontSize);
    	}
    }

    public static ITextRendererFontFallbackStrategy loadFontFallbackStrategy(){
        try {
            return  (ITextRendererFontFallbackStrategy) Class.forName(Configuration.valueFor("xr.text.font-fallback-strategy", "org.xhtmlrenderer.swing.Java2DTextRenderer$DefaultTextRendererFontFallbackStrategy")).newInstance();
        } catch (Exception e1) {
            throw new RuntimeException("Error loading font fallback strategy: " + e1.getMessage(), e1);
        }
    }

    public Java2DTextRenderer() {
        scale = Configuration.valueAsFloat("xr.text.scale", 1.0f);
        threshold = Configuration.valueAsFloat("xr.text.aa-fontsize-threshhold", 25);
		fallbackStrategy = loadFontFallbackStrategy();
        Object dummy = new Object();

        Object aaHint = Configuration.valueFromClassConstant("xr.text.aa-rendering-hint", dummy);
        if (aaHint == dummy) {
            try {
                Map map;
                // we should be able to look up the "recommended" AA settings (that correspond to the user's
                // desktop preferences and machine capabilities
                // see: http://java.sun.com/javase/6/docs/api/java/awt/doc-files/DesktopProperties.html
                Toolkit tk = Toolkit.getDefaultToolkit();
                map = (Map) (tk.getDesktopProperty("awt.font.desktophints"));
                antiAliasRenderingHint = map.get(RenderingHints.KEY_TEXT_ANTIALIASING);
            } catch (Exception e) {
                // conceivably could get an exception in a webstart environment? not sure
                antiAliasRenderingHint = RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
            }
        } else {
            antiAliasRenderingHint = aaHint;
        }
        if("true".equals(Configuration.valueFor("xr.text.fractional-font-metrics", "false"))) {
            fractionalFontMetricsHint = RenderingHints.VALUE_FRACTIONALMETRICS_ON;
        } else {
            fractionalFontMetricsHint = RenderingHints.VALUE_FRACTIONALMETRICS_OFF;
        }
    }

    /** {@inheritDoc} */
    public void drawString(OutputDevice outputDevice, String string, float x, float y ) {
        Object aaHint = null;
        Object fracHint;
        Graphics2D graphics = ((Java2DOutputDevice)outputDevice).getGraphics();
        final Font font = graphics.getFont();
		if ( font.getSize() > threshold ) {
            aaHint = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, antiAliasRenderingHint );
        }
        fracHint = graphics.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalFontMetricsHint);

        /*
         * We have to check if we can render all characters with this font. If some characters can not be rendered, we
         * should use a fallback to not render a rectangle char
         */
        boolean canUseSimplePaint = determineCanUseSimplePaint(string, font);
		if (canUseSimplePaint)
			// Just use a simple paint
			graphics.drawString(string, (int) x, (int) y);
		else {
			/*
			 * No, does not work. We create some ugly fallback using some default font....
			 */
			AttributedString fallbackString = createFallbackString(string, font, fallbackStrategy, font.getSize2D());
			TextLayout textLayout = new TextLayout(fallbackString.getIterator(), graphics.getFontRenderContext());
			textLayout.draw(graphics, x, y);
			//graphics.drawString(fallbackString.getIterator(), (int) x, (int) y);
		}

        if ( font.getSize() > threshold ) {
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, aaHint );
        }
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fracHint);
    }

	private boolean determineCanUseSimplePaint(String string, final Font font) {
        /*
         * For now we *always* render the string using the AttributedString, because
         * otherweise the ligatures in high quality fonts are not drawn correctly.
         */
        if( string.length() > 0 )
            return false;
        for( int i = 0; i < string.length();  ) {
        	int codePoint = string.codePointAt(i);
        	if(!font.canDisplay(codePoint))
        		return false;
        	i += Character.charCount(codePoint);
        }
		return true;
	}

    // http://stackoverflow.com/a/9482676
    public static AttributedString createFallbackString(String text, Font mainFont,ITextRendererFontFallbackStrategy fontFallbackStrategy, float fontSize) {
        AttributedString result = new AttributedString(text);

        int textLength = text.length();
        result.addAttribute(TextAttribute.FONT, mainFont, 0, textLength);

		/*
		 * On Linux DejaVu needs special handling, because its a system font.
		 * Sometimes the symbols provided by the system font are messed up. This
		 * strange behavoir is not reproducable and only happens after the java
		 * process is running for days / weeks.
		 */
		boolean isDejaVu = mainFont.getName().equals("DejaVu Sans");
		int[] dejaVuBlacklistCodePoints = { 8222, 8220 };

        Font fallbackFont = null;
        int fallbackBegin = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
			boolean needsFallback = !mainFont.canDisplay(codePoint);
			if (isDejaVu) {
				for (int cp : dejaVuBlacklistCodePoints)
					if (codePoint == cp)
						needsFallback = true;
			}
            Font curFallback = null;
            if( needsFallback )
            	curFallback = fontFallbackStrategy.decideFallBackFor(codePoint, fontSize);

            if (curFallback != fallbackFont) {
            	if( fallbackFont != null)
                    result.addAttribute(TextAttribute.FONT, fallbackFont, fallbackBegin, i);
                fallbackFont = curFallback;
                if (fallbackFont != null)
                    fallbackBegin = i;
            }

            i += Character.charCount(codePoint);
        }
        if( fallbackFont != null)  {
        	// Also apply the fallback at the end of the string.
            result.addAttribute(TextAttribute.FONT, fallbackFont, fallbackBegin, text.length());
        }
        return result;
    }

    /* Only used to draw justified text */
    public void drawString(
            OutputDevice outputDevice, String string, float x, float y, JustificationInfo info) {
        Object aaHint = null;
        Object fracHint = null;
        Graphics2D graphics = ((Java2DOutputDevice)outputDevice).getGraphics();
        if ( graphics.getFont().getSize() > threshold ) {
            aaHint = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, antiAliasRenderingHint );
        }
        fracHint = graphics.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalFontMetricsHint);

	AttributedString fallbackString = createFallbackString(string, graphics.getFont(),
		fallbackStrategy, graphics.getFont().getSize2D());

        GlyphVector vector = graphics.getFont().createGlyphVector(
                graphics.getFontRenderContext(), fallbackString.getIterator());

        adjustGlyphPositions(string, info, vector);

        graphics.drawGlyphVector(vector, x, y);

        if ( graphics.getFont().getSize() > threshold ) {
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, aaHint );
        }
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fracHint);
    }

    private void adjustGlyphPositions(
            String string, JustificationInfo info, GlyphVector vector) {
        float adjust = 0.0f;
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (i != 0) {
                Point2D point = vector.getGlyphPosition(i);
                vector.setGlyphPosition(
                        i, new Point2D.Double(point.getX() + adjust, point.getY()));
            }
            if (c == ' ' || c == '\u00a0' || c == '\u3000') {
                adjust += info.getSpaceAdjust();
            } else {
                adjust += info.getNonSpaceAdjust();
            }
        }
    }

    public void drawGlyphVector(OutputDevice outputDevice, FSGlyphVector fsGlyphVector, float x, float y ) {
        Object aaHint = null;
        Object fracHint = null;
        Graphics2D graphics = ((Java2DOutputDevice)outputDevice).getGraphics();

        if ( graphics.getFont().getSize() > threshold ) {
            aaHint = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, antiAliasRenderingHint );
        }
        fracHint = graphics.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalFontMetricsHint);

        GlyphVector vector = ((AWTFSGlyphVector)fsGlyphVector).getGlyphVector();
        graphics.drawGlyphVector(vector, (int)x, (int)y );
        if ( graphics.getFont().getSize() > threshold ) {
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, aaHint );
        }
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fracHint);
    }

    /** {@inheritDoc} */
    public void setup(FontContext fontContext) {
        //Uu.p("setup graphics called");
//        ((Java2DFontContext)fontContext).getGraphics().setRenderingHint(
//                RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF );
    }

    public void setFontScale( float scale ) {
        this.scale = scale;
    }

    public void setSmoothingThreshold( float fontsize ) {
        threshold = fontsize;
    }

    public void setSmoothingLevel( int level ) { /* no-op */ }

    public FSFontMetrics getFSFontMetrics(FontContext fc, FSFont font, String string ) {
        Object fracHint = null;
        Graphics2D graphics = ((Java2DFontContext)fc).getGraphics();
        fracHint = graphics.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalFontMetricsHint);

        Font awtFont = ((AWTFSFont)font).getAWTFont();
        LineMetricsAdapter adapter = new LineMetricsAdapter(
                    awtFont.getLineMetrics(
                            string, graphics.getFontRenderContext()));

        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fracHint);
        return adapter;
    }

    public int getWidth(FontContext fc, FSFont font, String string) {
        boolean canUseSimplePaint = determineCanUseSimplePaint(string, ((AWTFSFont)font).getAWTFont());
        Object fracHint = null;
        Graphics2D graphics = ((Java2DFontContext)fc).getGraphics();
        fracHint = graphics.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalFontMetricsHint);
        Font awtFont = ((AWTFSFont)font).getAWTFont();
        int width = 0;

		Rectangle2D stringBounds, visualBounds;
        FontMetrics fontMetrics = graphics.getFontMetrics(awtFont);
        stringBounds = fontMetrics.getStringBounds(string, graphics);
        if( canUseSimplePaint ) {
            GlyphVector vector = awtFont.createGlyphVector(graphics.getFontRenderContext(), string);
            visualBounds = vector.getVisualBounds();
        }
        else {
			AttributedString fallbackString = createFallbackString(string, awtFont,
					fallbackStrategy, awtFont.getSize2D());
			TextLayout layout = new TextLayout(fallbackString.getIterator(), graphics.getFontRenderContext());
			visualBounds = layout.getBounds();
        }

		double minX = Math.min(visualBounds.getMinX(), stringBounds.getMinX());
		double maxX = Math.max(visualBounds.getMaxX(), stringBounds.getMaxX());
		double fullWidth = maxX - minX;
		if (fractionalFontMetricsHint == RenderingHints.VALUE_FRACTIONALMETRICS_ON) {
			width = (int) Math.round(fullWidth);
		} else {
			width = (int) Math.ceil(fullWidth);
		}

        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fracHint);
        return width;
    }

    public float getFontScale() {
        return this.scale;
    }

    public int getSmoothingLevel() {
        return 0;
    }

    /**
     * If anti-alias text is enabled, the value from RenderingHints to use for AA smoothing in Java2D. Defaults to
     * {@link java.awt.RenderingHints#VALUE_TEXT_ANTIALIAS_ON}.
     *
     * @return Current AA rendering hint
     */
    public Object getRenderingHints() {
        return antiAliasRenderingHint;
    }

    /**
     * If anti-alias text is enabled, the value from RenderingHints to use for AA smoothing in Java2D. Defaults to
     * {@link java.awt.RenderingHints#VALUE_TEXT_ANTIALIAS_ON}.
     *
     * @param renderingHints  rendering hint for AA smoothing in Java2D
     */
    public void setRenderingHints(Object renderingHints) {
        this.antiAliasRenderingHint = renderingHints;
    }

    public float[] getGlyphPositions(OutputDevice outputDevice, FSFont font, String text) {
        Object aaHint = null;
        Object fracHint = null;
        Graphics2D graphics = ((Java2DOutputDevice)outputDevice).getGraphics();
        Font awtFont = ((AWTFSFont)font).getAWTFont();

        if (awtFont.getSize() > threshold ) {
            aaHint = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, antiAliasRenderingHint );
        }
        fracHint = graphics.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalFontMetricsHint);


        GlyphVector vector = awtFont.createGlyphVector(
                graphics.getFontRenderContext(),
                text);
        float[] result = vector.getGlyphPositions(0, text.length() + 1, null);

        if (awtFont.getSize() > threshold ) {
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, aaHint );
        }
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fracHint);

        return result;
    }

    public Rectangle getGlyphBounds(OutputDevice outputDevice, FSFont font, FSGlyphVector fsGlyphVector, int index, float x, float y) {
        Object aaHint = null;
        Object fracHint = null;
        Graphics2D graphics = ((Java2DOutputDevice)outputDevice).getGraphics();
        Font awtFont = ((AWTFSFont)font).getAWTFont();

        if (awtFont.getSize() > threshold ) {
            aaHint = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, antiAliasRenderingHint );
        }
        fracHint = graphics.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalFontMetricsHint);

        GlyphVector vector = ((AWTFSGlyphVector)fsGlyphVector).getGlyphVector();

        Rectangle result = vector.getGlyphPixelBounds(index, graphics.getFontRenderContext(), x, y);

        if (awtFont.getSize() > threshold ) {
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, aaHint );
        }
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fracHint);

        return result;
    }

    public float[] getGlyphPositions(OutputDevice outputDevice, FSFont font, FSGlyphVector fsGlyphVector) {
        Object aaHint = null;
        Object fracHint = null;
        Graphics2D graphics = ((Java2DOutputDevice)outputDevice).getGraphics();
        Font awtFont = ((AWTFSFont)font).getAWTFont();

        if (awtFont.getSize() > threshold ) {
            aaHint = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, antiAliasRenderingHint );
        }
        fracHint = graphics.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalFontMetricsHint);

        GlyphVector vector = ((AWTFSGlyphVector)fsGlyphVector).getGlyphVector();

        float[] result = vector.getGlyphPositions(0, vector.getNumGlyphs() + 1, null);

        if (awtFont.getSize() > threshold ) {
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, aaHint );
        }
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fracHint);

        return result;
    }

    /* Only used for selection */
    public FSGlyphVector getGlyphVector(OutputDevice outputDevice, FSFont font, String text) {
        Object aaHint = null;
        Object fracHint = null;
        Graphics2D graphics = ((Java2DOutputDevice)outputDevice).getGraphics();
        Font awtFont = ((AWTFSFont)font).getAWTFont();
        
        if (awtFont.getSize() > threshold ) {
            aaHint = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, antiAliasRenderingHint );
        }
        fracHint = graphics.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalFontMetricsHint);

        AttributedString fallbackString = createFallbackString(text, graphics.getFont(),
                fallbackStrategy, graphics.getFont().getSize2D());
        GlyphVector vector = awtFont.createGlyphVector(
                graphics.getFontRenderContext(),
                fallbackString.getIterator());
        
        if (awtFont.getSize() > threshold ) {
            graphics.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, aaHint );
        }
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fracHint);
        
        return new AWTFSGlyphVector(vector);
    }
}

