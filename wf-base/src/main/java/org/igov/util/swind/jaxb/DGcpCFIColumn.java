//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2016.03.27 at 04:30:25 PM EEST 
//


package org.igov.util.swind.jaxb;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;


/**
 * <p>Java class for DGcpCFIColumn complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DGcpCFIColumn">
 *   &lt;simpleContent>
 *     &lt;extension base="&lt;>DGcpCFI">
 *       &lt;attribute name="ROWNUM" use="required" type="{}rowInt" />
 *     &lt;/extension>
 *   &lt;/simpleContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DGcpCFIColumn", propOrder = {
    "value"
})
public class DGcpCFIColumn {

    @XmlValue
    protected String value;
    @XmlAttribute(name = "ROWNUM", required = true)
    protected int rownum;

    /**
     * 
     * 				тип, що описує код показника "Класифікатора фінансових інструментів", число з ведучими нулями довжиною 8 знаків
     * 			
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value of the value property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Gets the value of the rownum property.
     * 
     */
    public int getROWNUM() {
        return rownum;
    }

    /**
     * Sets the value of the rownum property.
     * 
     */
    public void setROWNUM(int value) {
        this.rownum = value;
    }

}
