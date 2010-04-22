package gatchan.jedit.lucene;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import marker.FileMarker;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.analysis.TokenStream;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.io.VFS;
import org.gjt.sp.jedit.io.VFSFile;
import org.gjt.sp.jedit.io.VFSManager;
import org.gjt.sp.util.IOUtilities;
import org.gjt.sp.util.IntegerArray;
import org.gjt.sp.util.Log;

/*
 * A query result processor that collects the lines containing results,
 * using the highlighter to find matches within the files.
 */
public class MarkerListQueryProcessor implements ResultProcessor
{
	private final Index index;
	private final List<Object> results;
	private final int max;

	public MarkerListQueryProcessor(Index index,
	                                List<Object> results, int max)
	{
		this.index = index;
		this.results = results;
		this.max = max;
	}

	public boolean process(Query query, float score, Result result)
	{
		if (result instanceof LineResult)
		{
			LineResult lr = (LineResult) result;
			FileMarker marker = new FileMarker(lr.getPath(),
			                                   lr.getLine() - 1, lr.getText());
			results.add(marker);
		}
		else
		{
			String s = result.getPath();
			addLinesMatching(query, s, max - results.size());
		}
		return (results.size() < max);
	}

	private void addLinesMatching(Query query, String file, int max)
	{
		IntegerArray positions = new IntegerArray(30);
		Formatter sf = new SearchFormatter(positions, max);
		QueryScorer scorer = new QueryScorer(query);
		StringBuilder sb = new StringBuilder();
		List<Integer> lineStart = new ArrayList<Integer>(500);
		BufferedReader br = null;
		try
		{
			br = getReader(file);
			String s;
			char sep = '\n';
			while ((s = br.readLine()) != null)
			{
				if (sb.length() > 0)
					sb.append(sep);
				lineStart.add(sb.length());
				sb.append(s);
			}
			Highlighter h = new Highlighter(sf, scorer);
			h.setMaxDocCharsToAnalyze(sb.length());
			String text = sb.toString();
			TokenStream tokenStream = index.getAnalyzer().tokenStream("field", new StringReader(text));
			h.getBestFragments(tokenStream, text, 0);
		}
		catch (Exception e)
		{
			Log.log(Log.ERROR, this, e);
		}
		finally
		{
			IOUtilities.closeQuietly(br);
		}

		for (int i = 0; i < positions.getSize(); i += 2)
		{
			int tokenStart = positions.get(i), tokenEnd = positions.get(i + 1); 
			int start = tokenStart, stop = tokenEnd;
			// Find beginning and end of line
			while ((start >= 0) && (sb.charAt(start) != '\n'))
				start--;
			start++;
			while ((stop < sb.length()) && (sb.charAt(stop) != '\n'))
				stop++;
			String lineText = sb.substring(start, stop);
			int line = Collections.binarySearch(lineStart, tokenStart);
			if (line < 0)
				line = -line - 2;
			FileMarker marker = new FileMarker(file, line, lineText);
			int startOffset = tokenStart - start;
			int endOffset = startOffset + tokenEnd - tokenStart;
			if (endOffset > lineText.length())
				endOffset = lineText.length();
			marker.addSelection(marker.new Selection(startOffset,
				startOffset + tokenEnd - tokenStart));
			results.add(marker);
		}
	}

	private static BufferedReader getReader(String file)
	{
		VFS vfs = VFSManager.getVFSForPath(file);
		View view = jEdit.getActiveView();
		Object session = vfs.createVFSSession(file, view);
		BufferedReader reader = null;
		try
		{
			VFSFile vfsFile = vfs._getFile(session, file, view);
			reader = new BufferedReader(new InputStreamReader(
				vfsFile.getVFS()._createInputStream(session,
				                                    vfsFile.getPath(), false, view)));
		}
		catch (IOException e)
		{
			Log.log(Log.ERROR, MarkerListQueryProcessor.class, e);
		}
		return reader;
	}
}
