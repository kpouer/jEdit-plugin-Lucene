/*
 * AbstractIndex.java
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2009 Matthieu Casanova
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package gatchan.jedit.lucene;

import org.gjt.sp.jedit.AbstractOptionPane;
import org.gjt.sp.util.Log;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author Matthieu Casanova
 */
public class IndexManagement extends AbstractOptionPane
{
	private IndexOptionPanel indexOptionPanel;
	private DefaultListModel model;

	public IndexManagement()
	{
		super("LuceneIndexManagement");
		setLayout(new BorderLayout());
	}

	@Override
	protected void _init()
	{
		model = new DefaultListModel();
		final JList indexList = new JList(model);
		JScrollPane leftScroll = new JScrollPane(indexList);
		indexOptionPanel = new IndexOptionPanel();
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, indexOptionPanel);
		indexList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		indexList.getSelectionModel().addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent e)
			{
				int row = e.getFirstIndex();
				if (row > -1)
				{
					indexOptionPanel.setIndex((String) indexList.getSelectedValue());
				}
			}
		});

		add(split);
		updateListModel();
	}

	private void updateListModel()
	{
		model.clear();
		String[] items = LucenePlugin.instance.getIndexes();
		for (String name : items)
		{
			model.addElement(name);
		}
	}

	@Override
	protected void _save()
	{
	}

	private class IndexOptionPanel extends JPanel
	{
		private final JTextField indexNameField;
		private String indexName;

		private JButton optimize;
		private JButton delete;
		private JButton reindex;

		private IndexOptionPanel()
		{
			super(new SpringLayout());
			SpringLayout layout = (SpringLayout) getLayout();
			indexNameField = new JTextField();
			indexNameField.setEditable(false);

			optimize = new JButton("Optimize");
			delete = new JButton("Delete");
			reindex = new JButton("Re-index");


			JLabel indexNameLabel = new JLabel("Index name:");

			// label at 5 pixel of the left and top border
			layout.putConstraint(SpringLayout.WEST, indexNameLabel, 5, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.NORTH, indexNameLabel, 5, SpringLayout.NORTH, this);


			// indexNameField at 5 pixels from label and 5 pixels from east border
			layout.putConstraint(SpringLayout.WEST, indexNameField, 5, SpringLayout.EAST, indexNameLabel);
			layout.putConstraint(SpringLayout.NORTH, indexNameField, 5, SpringLayout.NORTH, this);
			layout.putConstraint(SpringLayout.EAST, this, 5, SpringLayout.EAST, indexNameField);

			// optimize
			layout.putConstraint(SpringLayout.WEST, optimize, 5, SpringLayout.WEST, this);
			layout.putConstraint(SpringLayout.NORTH, optimize, 10, SpringLayout.SOUTH, indexNameLabel);

			// delete
			layout.putConstraint(SpringLayout.WEST, delete, 5, SpringLayout.EAST, optimize);
			layout.putConstraint(SpringLayout.NORTH, delete, 10, SpringLayout.SOUTH, indexNameLabel);

			// reindex
			layout.putConstraint(SpringLayout.WEST, reindex, 5, SpringLayout.EAST, delete);
			layout.putConstraint(SpringLayout.NORTH, reindex, 10, SpringLayout.SOUTH, indexNameLabel);

			optimize.setEnabled(false);
			delete.setEnabled(false);
			reindex.setEnabled(false);

			add(indexNameLabel);
			add(indexNameField);
			add(optimize);
			add(delete);
			add(reindex);


			MyActionListener actionListener = new MyActionListener();
			optimize.addActionListener(actionListener);
			delete.addActionListener(actionListener);
			reindex.addActionListener(actionListener);

		}

		public void setIndex(String name)
		{
			this.indexName = name;
			indexNameField.setText(name);
			if (name == null)
			{
				optimize.setEnabled(false);
				delete.setEnabled(false);
				reindex.setEnabled(false);
			}
			else
			{
				Index index = LucenePlugin.instance.getIndex(name);
				optimize.setEnabled(!index.isOptimized());
				delete.setEnabled(true);
				reindex.setEnabled(true);
			}
		}

		private class MyActionListener implements ActionListener
		{
			public void actionPerformed(ActionEvent e)
			{
				if (e.getSource() == optimize)
				{
					Log.log(Log.NOTICE, this, "Optimize " + indexName + " asked");
					Index index = LucenePlugin.instance.getIndex(indexName);
					index.optimize();
					index.commit();
					Log.log(Log.NOTICE, this, "Optimize " + indexName + " DONE");
				}
				else if (e.getSource() == delete)
				{
					Log.log(Log.NOTICE, this, "Delete " + indexName + " asked");
					LucenePlugin.instance.removeIndex(indexName);
					updateListModel();
					Log.log(Log.NOTICE, this, "Delete " + indexName + " DONE");
				}
				else if (e.getSource() == reindex)
				{
					Log.log(Log.NOTICE, this, "Reindex " + indexName + " asked");
					Index index = LucenePlugin.instance.getIndex(indexName);
					index.reindex();
					Log.log(Log.NOTICE, this, "Reindex " + indexName + " DONE");
				}
			}
		}
	}
}
