/********************************************************************************
 * Copyright (c) 2002, 2006 IBM Corporation. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is 
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Initial Contributors:
 * The following IBM employees contributed to the Remote System Explorer
 * component that contains this file: David McKnight, Kushal Munir, 
 * Michael Berger, David Dykstal, Phil Coulthard, Don Yantzi, Eric Simpson, 
 * Emily Bruner, Mazen Faraj, Adrian Storisteanu, Li Ding, and Kent Hawley.
 * 
 * Contributors:
 * {Name} (company) - description of contribution.
 ********************************************************************************/

package org.eclipse.rse.ui.view.scratchpad;

import java.util.Hashtable;
import java.util.Map;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.rse.ui.view.ISystemViewElementAdapter;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.model.IWorkbenchAdapter;


/**
 * This is the content and label provider for the SystemScratchpadView.
 * This class is used both to populate the SystemScratchpadView but also
 * to resolve the icon and labels for the cells in the table/tree.
 * 
 */
public class SystemScratchpadViewProvider implements ILabelProvider, ITreeContentProvider
{


	private ListenerList listeners = new ListenerList(1);


	/**
	 * The cache of images that have been dispensed by this provider.
	 * Maps ImageDescriptor->Image.
	 */
	private Map imageTable = new Hashtable(40);
	private SystemScratchpadView _view;

	public SystemScratchpadViewProvider(SystemScratchpadView view)
	{
		super();
		_view = view;
	}

	public void inputChanged(Viewer visualPart, Object oldInput, Object newInput)
	{
	}



	public boolean isDeleted(Object element)
	{
		return false;
	}

	public Object[] getChildren(Object object)
	{
		return getElements(object);
	}

	public Object getParent(Object object)
	{
		return getAdapterFor(object).getParent(object);
	}

	public boolean hasChildren(Object object)
	{
		ISystemViewElementAdapter adapter = getAdapterFor(object);
		if (adapter != null)
		{
			return adapter.hasChildren(object);
		}
		else if (object instanceof IAdaptable) 
		{
			IWorkbenchAdapter wa = (IWorkbenchAdapter)((IAdaptable)object).getAdapter(IWorkbenchAdapter.class);
			if (wa != null)
				return wa.getChildren(object).length > 0;
		}
		return false;
	}

	public Object getElementAt(Object object, int i)
	{

		return null;
	}

	protected ISystemViewElementAdapter getAdapterFor(Object object)
	{
	    if  (object instanceof IAdaptable)
	    {
	    	IAdaptable adapt = (IAdaptable) object;
			ISystemViewElementAdapter result = (ISystemViewElementAdapter) adapt.getAdapter(ISystemViewElementAdapter.class);
			if (result != null)
			{
				result.setPropertySourceInput(object);
				result.setViewer(_view);
				return result;
			}
	    }
		return null;
	}

	public Object[] getElements(Object object)
	{
		Object[] results = null;

			if (object instanceof IAdaptable)
			{
				ISystemViewElementAdapter adapter = getAdapterFor(object);
				if (adapter != null && adapter.hasChildren(object))
				{
					results = adapter.getChildren(object);
				}
				else
				{
					IWorkbenchAdapter wa = (IWorkbenchAdapter)((IAdaptable)object).getAdapter(IWorkbenchAdapter.class);
					if (wa != null)
						return wa.getChildren(object);
				}
			}
		if (results == null)
		{
			return new Object[0];
		}

		return results;
	}

	public String getText(Object object)
	{
		if (object instanceof String)
		{
			return (String)object;
		}
		ISystemViewElementAdapter adapter = getAdapterFor(object);
		if (adapter != null)
		{
			return adapter.getText(object);
		}
		else if (object instanceof IAdaptable)
		{
			IWorkbenchAdapter wa = (IWorkbenchAdapter)((IAdaptable)object).getAdapter(IWorkbenchAdapter.class);
			if (wa != null)
			{
				return wa.getLabel(object);
			}
		}
		return object.toString();
	}

	public Image getImage(Object object)
	{
		Image image = null;
		if (object instanceof String)
		{
			return null;
		}
		ISystemViewElementAdapter adapter = getAdapterFor(object);
		if (adapter != null)
		{
			ImageDescriptor descriptor = adapter.getImageDescriptor(object);
	
			
			if (descriptor != null)
			{
				Object iobj = imageTable.get(descriptor);
				if (iobj == null)
				{
					image = descriptor.createImage();
					imageTable.put(descriptor, image);
				}
				else
				{
					image = (Image) iobj;
				}
			}
			return image;
		}
		else if (object instanceof IAdaptable)
		{
			IWorkbenchAdapter wa = (IWorkbenchAdapter)((IAdaptable)object).getAdapter(IWorkbenchAdapter.class);
			if (wa != null)
			{
				ImageDescriptor descriptor = wa.getImageDescriptor(object);
				if (descriptor != null)
				{
					Object iobj = imageTable.get(descriptor);
					if (iobj == null)
					{
						image = descriptor.createImage();
						imageTable.put(descriptor, image);
					}
					else
					{
						image = (Image) iobj;
					}
				}
				return image;
			}
		}
		return null;
	}


	public void addListener(ILabelProviderListener listener)
	{
		listeners.add(listener);
	}

	public boolean isLabelProperty(Object element, String property)
	{
		return true;
	}

	public void removeListener(ILabelProviderListener listener)
	{
		listeners.remove(listener);
	}

	public void dispose()
	{
		// TODO Auto-generated method stub
		
	}
	
	
}