/**
 * 
 */
package org.nuxeo.ecm.quota.count;

import static org.nuxeo.ecm.core.schema.FacetNames.FOLDERISH;
import static org.nuxeo.ecm.quota.count.Constants.DOCUMENTS_COUNT_STATISTICS_CHILDREN_COUNT_PROPERTY;
import static org.nuxeo.ecm.quota.count.Constants.DOCUMENTS_COUNT_STATISTICS_DESCENDANTS_COUNT_PROPERTY;
import static org.nuxeo.ecm.quota.count.Constants.DOCUMENTS_COUNT_STATISTICS_FACET;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.LifeCycleConstants;
import org.nuxeo.ecm.core.api.model.DeltaLong;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater;
import org.nuxeo.ecm.quota.QuotaStatsInitialWork;

/**
 * @author <a href="mailto:vdutat@nuxeo.com">Vincent Dutat</a>
 * @since 6.0
 *
 */
public class DeletedDocumentsCountUpdater extends AbstractQuotaStatsUpdater {

    protected static Log LOGGER = LogFactory.getLog(DeletedDocumentsCountUpdater.class);

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#needToProcessEventOnDocument(org.nuxeo.ecm.core.event.Event, org.nuxeo.ecm.core.api.DocumentModel)
	 */
	@Override
	protected boolean needToProcessEventOnDocument(Event event, DocumentModel doc) {
		return true;
	}

    protected void updateParentChildrenCount(CoreSession session, DocumentModel parent, long count)
            throws ClientException {
        Number previous;
        if (parent.hasFacet(DOCUMENTS_COUNT_STATISTICS_FACET)) {
            previous = (Number) parent.getPropertyValue(DOCUMENTS_COUNT_STATISTICS_CHILDREN_COUNT_PROPERTY);
        } else {
            parent.addFacet(DOCUMENTS_COUNT_STATISTICS_FACET);
            previous = null;
        }
        Number childrenCount = DeltaLong.deltaOrLong(previous, count);
        parent.setPropertyValue(DOCUMENTS_COUNT_STATISTICS_CHILDREN_COUNT_PROPERTY, childrenCount);
        setSystemContextData(parent);
        session.saveDocument(parent);
    }

    protected void updateCountStatistics(CoreSession session, DocumentModel doc, List<DocumentModel> ancestors,
            long count) throws ClientException {
        if (ancestors == null || ancestors.isEmpty()) {
            return;
        }
        if (count == 0) {
            return;
        }

        if (!doc.hasFacet(FOLDERISH)) {
            DocumentModel parent = ancestors.get(0);
            updateParentChildrenCount(session, parent, count);
        }

        for (DocumentModel ancestor : ancestors) {
            Number previous;
            if (ancestor.hasFacet(DOCUMENTS_COUNT_STATISTICS_FACET)) {
                previous = (Number) ancestor.getPropertyValue(DOCUMENTS_COUNT_STATISTICS_DESCENDANTS_COUNT_PROPERTY);
            } else {
                ancestor.addFacet(DOCUMENTS_COUNT_STATISTICS_FACET);
                previous = null;
            }
            Number descendantsCount = DeltaLong.deltaOrLong(previous, count);
            ancestor.setPropertyValue(DOCUMENTS_COUNT_STATISTICS_DESCENDANTS_COUNT_PROPERTY, descendantsCount);
            setSystemContextData(ancestor);
            session.saveDocument(ancestor);
        }

        session.save();
    }

    protected long getCount(DocumentModel doc) throws ClientException {
        if (doc.hasFacet(FOLDERISH)) {
            if (doc.hasFacet(DOCUMENTS_COUNT_STATISTICS_FACET)) {
                Number count = (Number) doc.getPropertyValue(DOCUMENTS_COUNT_STATISTICS_DESCENDANTS_COUNT_PROPERTY);
                return count == null ? 0 : count.longValue();
            } else {
                return 0;
            }
        } else {
            return 1;
        }
    }

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#processDocumentTrashOp(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.core.event.impl.DocumentEventContext)
	 */
	@Override
	protected void processDocumentTrashOp(CoreSession session, DocumentModel doc,
			DocumentEventContext ctx) throws ClientException {
		LOGGER.info("<processDocumentTrashOp> ");
		String transition = (String) ctx.getProperty(LifeCycleConstants.TRANSTION_EVENT_OPTION_TRANSITION);
        List<DocumentModel> ancestors = getAncestors(session, doc);
        long docCount = getCount(doc);
        if (LifeCycleConstants.DELETE_TRANSITION.equals(transition)) {
        	updateCountStatistics(session, doc, ancestors, -docCount);
        } else if (LifeCycleConstants.UNDELETE_TRANSITION.equals(transition)) {
        	updateCountStatistics(session, doc, ancestors, docCount);
        }
	}

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.QuotaStatsUpdater#computeInitialStatistics(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.quota.QuotaStatsInitialWork)
	 */
	@Override
	public void computeInitialStatistics(CoreSession session, QuotaStatsInitialWork work) {
		// TODO
		// if DocumentsCountUpdater#computeInitialStatistics() is called, count of documents in 'deleted' state is lost
	}

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#handleException(org.nuxeo.ecm.core.api.ClientException, org.nuxeo.ecm.core.event.Event)
	 */
	@Override
	protected ClientException handleException(ClientException arg0, Event arg1) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#processDocumentAboutToBeRemoved(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.core.event.impl.DocumentEventContext)
	 */
	@Override
	protected void processDocumentAboutToBeRemoved(CoreSession arg0,
			DocumentModel arg1, DocumentEventContext arg2)
			throws ClientException {

	}

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#processDocumentBeforeRestore(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.core.event.impl.DocumentEventContext)
	 */
	@Override
	protected void processDocumentBeforeRestore(CoreSession arg0,
			DocumentModel arg1, DocumentEventContext arg2)
			throws ClientException {

	}

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#processDocumentBeforeUpdate(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.core.event.impl.DocumentEventContext)
	 */
	@Override
	protected void processDocumentBeforeUpdate(CoreSession arg0,
			DocumentModel arg1, DocumentEventContext arg2)
			throws ClientException {
	}

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#processDocumentCheckedIn(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.core.event.impl.DocumentEventContext)
	 */
	@Override
	protected void processDocumentCheckedIn(CoreSession arg0,
			DocumentModel arg1, DocumentEventContext arg2)
			throws ClientException {
	}

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#processDocumentCheckedOut(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.core.event.impl.DocumentEventContext)
	 */
	@Override
	protected void processDocumentCheckedOut(CoreSession arg0,
			DocumentModel arg1, DocumentEventContext arg2)
			throws ClientException {
	}

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#processDocumentCopied(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.core.event.impl.DocumentEventContext)
	 */
	@Override
	protected void processDocumentCopied(CoreSession arg0, DocumentModel arg1,
			DocumentEventContext arg2) throws ClientException {
	}

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#processDocumentCreated(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.core.event.impl.DocumentEventContext)
	 */
	@Override
	protected void processDocumentCreated(CoreSession arg0, DocumentModel arg1,
			DocumentEventContext arg2) throws ClientException {
	}

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#processDocumentMoved(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.core.event.impl.DocumentEventContext)
	 */
	@Override
	protected void processDocumentMoved(CoreSession arg0, DocumentModel arg1,
			DocumentModel arg2, DocumentEventContext arg3)
			throws ClientException {
	}

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#processDocumentRestored(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.core.event.impl.DocumentEventContext)
	 */
	@Override
	protected void processDocumentRestored(CoreSession arg0,
			DocumentModel arg1, DocumentEventContext arg2)
			throws ClientException {
	}

	/* (non-Javadoc)
	 * @see org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater#processDocumentUpdated(org.nuxeo.ecm.core.api.CoreSession, org.nuxeo.ecm.core.api.DocumentModel, org.nuxeo.ecm.core.event.impl.DocumentEventContext)
	 */
	@Override
	protected void processDocumentUpdated(CoreSession arg0, DocumentModel arg1,
			DocumentEventContext arg2) throws ClientException {
	}

}
