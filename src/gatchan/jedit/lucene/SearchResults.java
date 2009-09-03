package gatchan.jedit.lucene;

import gatchan.jedit.lucene.Index.ActivityListener;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Vector;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;

import marker.FileMarker;
import marker.MarkerSetsPlugin;
import marker.tree.SourceLinkTree;
import marker.tree.SourceLinkTree.SourceLinkParentNode;
import marker.tree.SourceLinkTree.SubtreePopupMenuProvider;

import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.StandardUtilities;
import org.gjt.sp.util.Log;

@SuppressWarnings("serial")
public class SearchResults extends JPanel implements EBComponent
{
	private static final String MESSAGE_IDLE = "";
	private static final String MESSAGE_INDEXING = "Indexing";
	private JTextField searchField;
	private JTextField type;
	private JPanel mainPanel;
	private JList list;
	private JCheckBox lineResults;
	private JSpinner maxResults;
	private JComboBox indexes;
	private MyModel model;
	private SourceLinkTree tree;
	private IndexComboBoxModel indexModel;
	private JLabel indexStatus;
//	private JTextPane preview;

	public SearchResults()
	{
		super(new BorderLayout());
		
		lineResults = new JCheckBox("Line-based results");
		type = new JTextField(6);
		maxResults = new JSpinner(new SpinnerNumberModel(100, 1, 10000, 1));
		JPanel maxPanel = new JPanel(new BorderLayout());
		maxPanel.add(BorderLayout.WEST, new JLabel("Max results:"));
		maxPanel.add(BorderLayout.EAST, maxResults);

		indexStatus = new JLabel();
		String[] items = LucenePlugin.instance.getIndexes();
		indexModel = new IndexComboBoxModel(items);
		indexes = new JComboBox(indexModel);
		indexes.addActionListener(new ActionListener()
		{
			private Index prevIndex = null;
			private ActivityListener listener = new ActivityListener()
			{
				public void indexingEnded(Index index)
				{
					indexStatus.setText(MESSAGE_IDLE);
				}
				public void indexingStarted(Index index)
				{
					indexStatus.setText(MESSAGE_INDEXING);
				}
			}; 
			public void actionPerformed(ActionEvent e)
			{
				if (prevIndex != null)
					prevIndex.removeActivityListener(listener);
				Index index = getSelectedIndex();
				if (index == null)
					return;
				prevIndex = index;
				indexStatus.setText(index.isChanging() ? MESSAGE_INDEXING : MESSAGE_IDLE);
				index.addActivityListener(listener);
			}
		});

		JPanel panel = new JPanel(new BorderLayout());
		add(panel, BorderLayout.NORTH);
		panel.add(new JLabel("Search for:"), BorderLayout.WEST);
		searchField = new JTextField(40);
		MyActionListener actionListener = new MyActionListener();
		searchField.addActionListener(actionListener);
		type.addActionListener(actionListener);

		JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		optionsPanel.add(new JLabel("file type:"));
		optionsPanel.add(type);
		optionsPanel.add(lineResults);
		optionsPanel.add(maxPanel);
		optionsPanel.add(indexes);
		optionsPanel.add(indexStatus);
		panel.add(searchField, BorderLayout.CENTER);
		panel.add(optionsPanel, BorderLayout.EAST);
		model = new MyModel();
		list = new JList(model);
		tree = new SourceLinkTree(jEdit.getActiveView());

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.getSelectionModel().addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent e)
			{
				Object obj = list.getSelectedValue();
				View view = jEdit.getActiveView();
				if (obj instanceof String)
				{
					String path = (String) list.getSelectedValue();
					jEdit.openFile(view, path);
				}
				else if (obj instanceof FileMarker)
				{
					((FileMarker) obj).jump(view);
				}
			}
		});
//		HTMLDocument htmlDocument = new HTMLDocument();
//		preview = new JTextPane(htmlDocument);
//		preview.setContentType("text/html");
		mainPanel = new JPanel(new CardLayout());
		mainPanel.add(new JScrollPane(list), "list");
		mainPanel.add(new JScrollPane(tree), "tree");
		//add(mainPanel, new JScrollPane(tree));
		add(mainPanel, BorderLayout.CENTER);
//		add(new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(list), preview), BorderLayout.CENTER);
		if (indexes.getItemCount() > 0)
			indexes.setSelectedIndex(0);
	}

	private Index getSelectedIndex()
	{
		return LucenePlugin.instance.getIndex((String) indexes.getSelectedItem());
	}

	public void search(String text)
	{
		Log.log(Log.NOTICE, this, "Search for " + text);
		Index index = getSelectedIndex();
		if (index == null)
			return;

		final java.util.List<Object> files = new ArrayList<Object>();
		int max = ((Integer)(maxResults.getValue())).intValue();
		ResultProcessor processor;
		if (lineResults.isSelected())
			processor = new MarkerListQueryProcessor(index, files, max);
		else
			processor = new FileListQueryProcessor(index, files, max);
		index.search(text, max, processor);
		if (lineResults.isSelected())
		{
			SearchRootNode rootNode = new SearchRootNode(text);
			SourceLinkParentNode parent = tree.addSourceLinkParent(rootNode);
			FileMarker prev = null;
			int count = 0;
			for (Object o: files)
			{
				FileMarker marker = (FileMarker) o;
				if (! marker.equals(prev))
				{
					parent.addSourceLink(marker);
					prev = marker;
					count++;
				}
			}
			rootNode.addChildCount(count);
			TreeModel model = tree.getModel();
			if (model instanceof DefaultTreeModel)
				((DefaultTreeModel)model).nodeChanged(parent);
			((CardLayout) mainPanel.getLayout()).show(mainPanel, "tree");
		}
		else
		{
			model.setFiles(files);
			((CardLayout) mainPanel.getLayout()).show(mainPanel, "list");
		}
	}

	//{{{ addNotify() method
	public void addNotify()
	{
		super.addNotify();
		EditBus.addToBus(this);
	} //}}}

	//{{{ removeNotify() method
	public void removeNotify()
	{
		super.removeNotify();
		EditBus.removeFromBus(this);
	} //}}}

	public void handleMessage(EBMessage message)
	{
		if (message instanceof LuceneIndexUpdate)
		{
			String[] items = LucenePlugin.instance.getIndexes();
			indexModel.setIndexes(items);
		}
	}

	private static class MyModel extends AbstractListModel
	{
		private java.util.List<Object> files = new ArrayList<Object>();

		public void setFiles(java.util.List<Object> files)
		{
			this.files = files;
			fireContentsChanged(this, 0, files.size());
		}

		public int getSize()
		{
			return files.size();
		}

		public Object getElementAt(int index)
		{
			return files.get(index);
		}
	}

	private static class IndexComboBoxModel extends AbstractListModel implements ComboBoxModel
	{
		private String[] indexes;

		private String selectedItem;

		private IndexComboBoxModel(String[] indexes)
		{
			setIndexes(indexes);
		}

		public int getSize()
		{
			return indexes.length;
		}

		public Object getElementAt(int index)
		{
			return indexes[index];
		}

		public void setIndexes(String[] indexes)
		{
			this.indexes = indexes;
			if (indexes.length == 0)
			{
				selectedItem = null;
			}
			else
			{
				boolean selectedStillExists = false;
				for (String index : indexes)
				{
					if (StandardUtilities.objectsEqual(selectedItem, index))
					{
						selectedStillExists = true;
						break;
					}
				}
				if (!selectedStillExists)
					selectedItem = indexes[0];
			}
			fireContentsChanged(this, 0, indexes.length);
		}

		public void setSelectedItem(Object selectedItem)
		{
			this.selectedItem = (String) selectedItem;
		}

		public Object getSelectedItem()
		{
			return selectedItem;
		}
	}

	private class SearchRootNode implements SubtreePopupMenuProvider
	{
		private String text;
		public SearchRootNode(String searchText)
		{
			text = searchText;
		}
		public String toString()
		{
			return text;
		}
		public void addPopupMenuItemsFor(JPopupMenu popup,
				final SourceLinkParentNode parent,
				final DefaultMutableTreeNode node)
		{
			if (popup == null)
				return;
			popup.add(new AbstractAction("Toggle marker(s)") {
				public void actionPerformed(ActionEvent e) {
					Vector<FileMarker> markers = parent.getFileMarkers(node);
					for (FileMarker marker: markers)
						MarkerSetsPlugin.toggleMarker(marker);
				}
			});
		}
		public void addChildCount(int count)
		{
			text = text + " (" + count + ")";
		}
	}

	private class MyActionListener implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			String search = searchField.getText().trim();
			String fileType = type.getText().trim();
			if (fileType.length() != 0)
			{
				if (search.length() != 0)
					search = "(" + search + ") AND ";
				search += " +filetype:" + fileType;
			}
			search(search);
		}
	}
}
