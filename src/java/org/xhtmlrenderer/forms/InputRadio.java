
/* 
 * {{{ header & license 
 * Copyright (c) 2004 Joshua Marinacci 
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation; either version 2.1 
 * of the License, or (at your option) any later version. 
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the 
 * GNU Lesser General Public License for more details. 
 * 
 * You should have received a copy of the GNU Lesser General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA. 
 * }}} 
 */

package org.xhtmlrenderer.forms;

import java.util.*;
import java.awt.Dimension;
import java.awt.Point;
import javax.swing.*;
import javax.swing.JComponent;
import org.joshy.u;
import org.xhtmlrenderer.render.*;
import org.xhtmlrenderer.layout.*;
import org.w3c.dom.*;

public class InputRadio extends FormItemLayout {
    
    public InputRadio() {
    }
    
    public JComponent createComponent(Context c, Element elem) {
        JRadioButton comp = new JRadioButton();
        comp.setText("");
        comp.setOpaque(false);
        if(elem.hasAttribute("checked") &&
            elem.getAttribute("checked").equals("checked")) {
            comp.setSelected(true);
        }
        commonPrep(comp,elem);
        
        if(elem.hasAttribute("name")) {
            String name = elem.getAttribute("name");
            List other_comps = c.getInputFieldComponents(c.getForm(),name);
            if(other_comps.size() > 0) {
                for(int i=0; i<other_comps.size(); i++) {
                    Context.FormComponent other_comp = (Context.FormComponent)other_comps.get(i);
                    if(other_comp.component instanceof JRadioButton) {
                        JRadioButton other_radio = (JRadioButton)other_comp.component;
                        //u.p("found a matching component: " + other_radio);
                    }
                }
            }
        }
        return comp;
    }
    
}