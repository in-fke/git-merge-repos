package org.nibor.git_merge_repos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merges the passed commit trees into one tree, adjusting directory structure if necessary (depends on options from user).
 */
public class SubtreeMerger {

	private static Logger logger = LoggerFactory.getLogger(SubtreeMerger.class);

	private final Repository repository;

	public SubtreeMerger(Repository repository) {
		this.repository = repository;
	}

	public ObjectId createMergeCommit(Map<SubtreeConfig, RevCommit> parentCommits, MergedRef mergedRef) throws IOException {
		PersonIdent latestIdent = getLatestPersonIdent(parentCommits.values());
		DirCache treeDirCache = createTreeDirCache(parentCommits, mergedRef);
		List<? extends ObjectId> parentIds = new ArrayList<>(parentCommits.values());
		try (ObjectInserter inserter = repository.newObjectInserter()) {
			ObjectId treeId = treeDirCache.writeTree(inserter);

			PersonIdent repositoryUser = new PersonIdent(repository);
			PersonIdent ident = new PersonIdent(repositoryUser, latestIdent.getWhen().getTime(), latestIdent.getTimeZoneOffset());
			CommitBuilder commitBuilder = new CommitBuilder();
			commitBuilder.setTreeId(treeId);
			commitBuilder.setAuthor(ident);
			commitBuilder.setCommitter(ident);
			commitBuilder.setMessage(mergedRef.getMessage());
			commitBuilder.setParentIds(parentIds);
			ObjectId mergeCommit = inserter.insert(commitBuilder);
			inserter.flush();
			return mergeCommit;
		}
	}

	private PersonIdent getLatestPersonIdent(Collection<RevCommit> commits) {
		PersonIdent latest = null;
		for (RevCommit commit : commits) {
			PersonIdent ident = commit.getCommitterIdent();
			Date when = ident.getWhen();
			if (latest == null || when.after(latest.getWhen())) {
				latest = ident;
			}
		}
		return latest;
	}

	private DirCache createTreeDirCache(Map<SubtreeConfig, RevCommit> parentCommits, MergedRef mergedRef) throws IOException {

		try (TreeWalk treeWalk = new TreeWalk(repository)) {
			treeWalk.setRecursive(true);
			addTrees(parentCommits, treeWalk);

			DirCacheBuilder builder = DirCache.newInCore().builder();
			while (treeWalk.next()) {
				AbstractTreeIterator iterator = getSingleTreeIterator(treeWalk, mergedRef);
				if (iterator == null) {
					throw new IllegalStateException("Tree walker did not return a single tree (should not happen): " + treeWalk.getPathString());
				}
				byte[] path = Arrays.copyOf(iterator.getEntryPathBuffer(), iterator.getEntryPathLength());
				DirCacheEntry entry = new DirCacheEntry(path);
				entry.setFileMode(iterator.getEntryFileMode());
				entry.setObjectId(iterator.getEntryObjectId());
				builder.add(entry);
			}
			if (overlaps.size() > 0) {
				for (OverlapEntry msg : overlaps) {
					logger.error(msg.toString());
				}
				throw new IllegalStateException();
			}
			builder.finish();
			return builder.getDirCache();
		}
	}

	private void addTrees(Map<SubtreeConfig, RevCommit> parentCommits, TreeWalk treeWalk) throws IOException {
		for (Map.Entry<SubtreeConfig, RevCommit> entry : parentCommits.entrySet()) {
			String directory = entry.getKey().getSubtreeDirectory();
			RevCommit parentCommit = entry.getValue();
			if (".".equals(directory)) {
				treeWalk.addTree(parentCommit.getTree());
			} else {
				byte[] prefix = directory.getBytes(RawParseUtils.UTF8_CHARSET);
				CanonicalTreeParser treeParser = new CanonicalTreeParser(prefix, treeWalk.getObjectReader(), parentCommit.getTree());
				treeWalk.addTree(treeParser);
			}
		}
	}

	private Collection<OverlapEntry> overlaps = new ArrayList<>();

	private static class OverlapEntry {
		private MergedRef mergedRef;
		private String entryPath1;
		private String entryPath2;

		public OverlapEntry(MergedRef mergedRef, String entryPath1, String entryPath2) {
			super();
			this.mergedRef = mergedRef;
			this.entryPath1 = entryPath1;
			this.entryPath2 = entryPath2;
		}

		@Override
		public String toString() {
			return "OverlapEntry [mergedRef=" + mergedRef.getRefName() + ", entryPath1=" + entryPath1 + ", entryPath2=" + entryPath2 + "]";
		}

	}

	private AbstractTreeIterator getSingleTreeIterator(TreeWalk treeWalk, MergedRef mergedRef) {
		AbstractTreeIterator result = null;
		int treeCount = treeWalk.getTreeCount();
		for (int i = 0; i < treeCount; i++) {
			AbstractTreeIterator it = treeWalk.getTree(i, AbstractTreeIterator.class);
			if (it != null) {
				if (result != null) {
//					String msg = "Trees of repositories overlap in path '"
//							+ it.getEntryPathString()
//							+ "'. "
//							+ "We can only merge non-overlapping trees, "
//							+ "so make sure the repositories have been prepared for that. "
//							+ "One possible way is to process each repository to move the root to a subdirectory first.\n"
//							+ "Existing entry (" + result.getClass().getSimpleName() + "): "
//							+ result.getEntryPathString() + "\n"
//							+ "Next entry (" + it.getClass().getSimpleName() + "): "
//							+ it.getEntryPathString() + "\n"
//							+ "Current commit:\n" + mergedRef.getMessage();
//					throw new IllegalStateException(msg);
					overlaps.add(new OverlapEntry(mergedRef, result.getEntryPathString(), it.getEntryPathString()));
				} else {
					result = it;
				}
			}
		}
		return result;
	}
}
