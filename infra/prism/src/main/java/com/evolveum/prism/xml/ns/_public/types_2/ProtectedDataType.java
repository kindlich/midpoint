//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2014.02.04 at 01:34:24 PM CET 
//


package com.evolveum.prism.xml.ns._public.types_2;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;

import org.apache.xml.security.exceptions.Base64DecodingException;
import org.apache.xml.security.utils.Base64;
import org.w3c.dom.Element;

import com.evolveum.midpoint.prism.crypto.ProtectedData;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.QNameUtil;


/**
 * 
 * 				TODO
 * 				May be either encrypted or hashed or provided in the clear (e.g. for debugging).
 * 				
 * 				This type is marked as "mixed" because it may have alternative representation where
 * 				just the plaintext value is presented as the only value.
 * 				
 * 				This is considered to be primitive built-in type for prism objects.
 * 			
 * 
 * <p>Java class for ProtectedDataType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ProtectedDataType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="encryptedData" type="{http://prism.evolveum.com/xml/ns/public/types-2}EncryptedDataType" minOccurs="0"/>
 *         &lt;any namespace='##other'/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ProtectedDataType", propOrder = {
    "content"
})
@XmlSeeAlso({
    ProtectedByteArrayType.class,
    ProtectedStringType.class
})
public abstract class ProtectedDataType<T> implements ProtectedData<T>, Serializable {
	
	public static final QName COMPLEX_TYPE = new QName("http://prism.evolveum.com/xml/ns/public/types-2", "ProtectedDataType");
	public final static QName F_ENCRYPTED_DATA = new QName("http://prism.evolveum.com/xml/ns/public/types-2", "encryptedData");
	public final static QName F_CLEAR_VALUE = new QName("http://prism.evolveum.com/xml/ns/public/types-2", "clearValue");
	
	public static final String NS_XML_ENC = "http://www.w3.org/2001/04/xmlenc#";
	public static final String NS_XML_DSIG = "http://www.w3.org/2000/09/xmldsig#";
	public static final QName F_XML_ENC_ENCRYPTED_DATA = new QName(NS_XML_ENC, "EncryptedData");
	public static final QName F_XML_ENC_ENCRYPTION_METHOD = new QName(NS_XML_ENC, "EncryptionMethod");
	public static final String ATTRIBUTE_XML_ENC_ALGORITHM = "Algorithm";
	public static final QName F_XML_ENC_ALGORITHM = new QName(NS_XML_ENC, ATTRIBUTE_XML_ENC_ALGORITHM);
	public static final QName F_XML_ENC_CIPHER_DATA = new QName(NS_XML_ENC, "CipherData");
	public static final QName F_XML_ENC_CIPHER_VALUE = new QName(NS_XML_ENC, "CipherValue");
	public static final QName F_XML_DSIG_KEY_INFO = new QName(NS_XML_DSIG, "KeyInfo");
	public static final QName F_XML_DSIG_KEY_NAME = new QName(NS_XML_DSIG, "KeyName");
	
	
	@XmlTransient
	private EncryptedDataType encryptedDataType;
	
	@XmlTransient
	private T clearValue;

    @XmlElementRef(name = "encryptedData", namespace = "http://prism.evolveum.com/xml/ns/public/types-2", type = JAXBElement.class)
    @XmlMixed
    @XmlAnyElement(lax = true)
    protected List<Object> content;

    /**
     * 
     * 				TODO
     * 				May be either encrypted or hashed or provided in the clear (e.g. for debugging).
     * 				
     * 				This type is marked as "mixed" because it may have alternative representation where
     * 				just the plaintext value is presented as the only value.
     * 				
     * 				This is considered to be primitive built-in type for prism objects.
     * 			Gets the value of the content property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the content property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getContent().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Object }
     * {@link String }
     * {@link JAXBElement }{@code <}{@link EncryptedDataType }{@code >}
     * 
     * 
     */
    public List<Object> getContent() {
        if (content == null) {
            content = new ContentList();
        }
        return this.content;
    }

    @Override
    public EncryptedDataType getEncryptedDataType() {
		return encryptedDataType;
	}

    @Override
	public void setEncryptedData(EncryptedDataType encryptedDataType) {
		this.encryptedDataType = encryptedDataType;
	}
	
    @Override
	public boolean isEncrypted() {
		return encryptedDataType != null;
	}
    
    public T getClearValue() {
    	return clearValue;
    }
    
    public void setClearValue(T clearValue) {
    	this.clearValue = clearValue;
    }
    
    @Override
	public void destroyCleartext() {
    	// Not perfect. But OK for now.
		clearValue = null;
	}
    
    private JAXBElement<EncryptedDataType> toJaxbElement(EncryptedDataType encryptedDataType) {
    	return new JAXBElement<EncryptedDataType>(F_ENCRYPTED_DATA, EncryptedDataType.class, encryptedDataType);
    }
    
    private void clearContent() {
    	encryptedDataType = null;
    }
    
    private boolean addContent(Object newObject) {
    	if (newObject instanceof String){
    		return true;
    	} else
    	if (newObject instanceof JAXBElement<?>) {
    		JAXBElement<?> jaxbElement = (JAXBElement<?>)newObject;
    		if (QNameUtil.match(F_ENCRYPTED_DATA, jaxbElement.getName())) {
    			encryptedDataType = (EncryptedDataType) jaxbElement.getValue();
    			return true;
    		} else {
    			throw new IllegalArgumentException("Attempt to add unknown JAXB element "+jaxbElement);
    		}
    	} else if (newObject instanceof Element) {
    		Element element = (Element)newObject;
    		QName elementName = DOMUtil.getQName(element);
    		if (QNameUtil.match(F_XML_ENC_ENCRYPTED_DATA, elementName)) {
    			encryptedDataType = convertXmlEncToEncryptedDate(element);
    			return true;
    		} else if (QNameUtil.match(F_CLEAR_VALUE, elementName)){
    			clearValue = (T) element.getTextContent();
    			return true;
    		} else {
    			throw new IllegalArgumentException("Attempt to add unknown DOM element "+elementName);
    		}
    	} else {
    		throw new IllegalArgumentException("Attempt to add unknown object "+newObject+" ("+newObject.getClass()+")");
    	}
    }
    
    private EncryptedDataType convertXmlEncToEncryptedDate(Element eEncryptedData) {
    	EncryptedDataType encryptedDataType = new EncryptedDataType();
    	Element eEncryptionMethod = DOMUtil.getChildElement(eEncryptedData, F_XML_ENC_ENCRYPTION_METHOD);
    	if (eEncryptionMethod != null) {
    		String algorithm = eEncryptionMethod.getAttribute(ATTRIBUTE_XML_ENC_ALGORITHM);
    		EncryptionMethodType encryptionMethodType = new EncryptionMethodType();
    		encryptionMethodType.setAlgorithm(algorithm);
			encryptedDataType.setEncryptionMethod(encryptionMethodType);
    	}
    	Element eKeyInfo = DOMUtil.getChildElement(eEncryptedData, F_XML_DSIG_KEY_INFO);
    	if (eKeyInfo != null) {
    		KeyInfoType keyInfoType = new KeyInfoType();
    		encryptedDataType.setKeyInfo(keyInfoType);
    		Element eKeyName = DOMUtil.getChildElement(eKeyInfo, F_XML_DSIG_KEY_NAME);
			if (eKeyName != null) {
				keyInfoType.setKeyName(eKeyName.getTextContent());
			}
    	}
    	Element eCipherData = DOMUtil.getChildElement(eEncryptedData, F_XML_ENC_CIPHER_DATA);
    	if (eCipherData != null) {
    		CipherDataType cipherDataType = new CipherDataType();
			encryptedDataType.setCipherData(cipherDataType);
			Element eCipherValue = DOMUtil.getChildElement(eCipherData, F_XML_ENC_CIPHER_VALUE);
			if (eCipherValue != null) {
				String cipherValue = eCipherValue.getTextContent();
				byte[] cipherValueBytes;
				try {
					cipherValueBytes = Base64.decode(cipherValue);
				} catch (Base64DecodingException e) {
					throw new IllegalArgumentException("Bad base64 encoding in CipherValue element: "+e.getMessage(),e);
				}
				cipherDataType.setCipherValue(cipherValueBytes);
			}
    	}
    	return encryptedDataType;
	}
    
    public boolean isEmpty() {
    	return encryptedDataType == null && clearValue == null;
    }
    
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((clearValue == null) ? 0 : clearValue.hashCode());
		result = prime * result + ((encryptedDataType == null) ? 0 : encryptedDataType.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ProtectedDataType other = (ProtectedDataType) obj;
		if (clearValue == null) {
			if (other.clearValue != null)
				return false;
		} else if (!clearValue.equals(other.clearValue))
			return false;
		if (encryptedDataType == null) {
			if (other.encryptedDataType != null)
				return false;
		} else if (!encryptedDataType.equals(other.encryptedDataType))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getClass().getSimpleName());
		sb.append("(");
		if (encryptedDataType != null) {
			sb.append("encrypted=");
			sb.append(encryptedDataType);
		}
		if (clearValue != null) {
			sb.append("clearValue=");
			sb.append(clearValue);
		}
		sb.append(")");
		return sb.toString();
	}

    protected void cloneTo(ProtectedDataType<T> cloned) {
        for (Object o : getContent()) {
            if (o instanceof JAXBElement<?>) {
                JAXBElement<?> je = (JAXBElement) o;
                Object v = je.getValue();
                if (v instanceof EncryptedDataType) {
                    EncryptedDataType edt = (EncryptedDataType) v;
                    cloned.addContent(new JAXBElement<EncryptedDataType>(je.getName(), (Class) je.getDeclaredType(), edt.clone()));
                } else {
                    throw new IllegalStateException("Unknown JAXB element "+je+ " in ProtectedDataType");
                }
            } else if (o instanceof Element) {
                cloned.addContent(((Element) o).cloneNode(true));
            } else if (o instanceof String) {
                cloned.addContent(o);           // will this work?
            } else {
                throw new IllegalStateException("Unknown object of type "+o.getClass()+ " in ProtectedDataType");
            }
        }
    }

    class ContentList implements List<Object>, Serializable {

		@Override
		public int size() {
			if (encryptedDataType != null) {
				return 1;
			} else {
				return 0;
			}
		}

		@Override
		public boolean isEmpty() {
			return encryptedDataType == null;
		}

		@Override
		public boolean contains(Object o) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public Iterator<Object> iterator() {
			return new Iterator<Object>() {
				private int index = 0;
				@Override
				public boolean hasNext() {
					return index == 0;
				}

				@Override
				public Object next() {
					if (index == 0) {
						index++;
						return toJaxbElement(encryptedDataType);
					} else {
						return null;
					}
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Override
		public Object[] toArray() {
			if (encryptedDataType == null) {
				return new Object[0];
			} else {
				Object[] a = new Object[1];
				a[0] = toJaxbElement(encryptedDataType);
				return a;
			}
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return (T[]) toArray();
		}

		@Override
		public boolean add(Object e) {
			return addContent(e);
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			for (Object e: c) {
				if (!contains(e)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public boolean addAll(Collection<? extends Object> c) {
			boolean changed = false;
			for (Object e: c) {
				if (add(e)) {
					changed = true;
				}
			}
			return changed;
		}

		@Override
		public boolean addAll(int index, Collection<? extends Object> c) {
			throw new UnsupportedOperationException("we are too lazy for this");
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			// We would normally throw an exception here. but JAXB is actually using it.
			clearContent();
		}

		@Override
		public Object get(int index) {
			if (index == 0) {
				return toJaxbElement(encryptedDataType);
			} else {
				return null;
			}
		}

		@Override
		public Object set(int index, Object element) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void add(int index, Object element) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object remove(int index) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int indexOf(Object o) {
			throw new UnsupportedOperationException("we are too lazy for this");
		}

		@Override
		public int lastIndexOf(Object o) {
			throw new UnsupportedOperationException("we are too lazy for this");
		}

		@Override
		public ListIterator<Object> listIterator() {
			throw new UnsupportedOperationException("we are too lazy for this");
		}

		@Override
		public ListIterator<Object> listIterator(int index) {
			throw new UnsupportedOperationException("we are too lazy for this");
		}

		@Override
		public List<Object> subList(int fromIndex, int toIndex) {
			throw new UnsupportedOperationException("we are too lazy for this");
		}
    	
    }
}
