from __future__ import annotations

import logging
import os
from typing import Any

logger = logging.getLogger(__name__)

# Check if Google Cloud Firestore is configured
FIRESTORE_AVAILABLE = False
try:
    from google.cloud import firestore  # type: ignore

    FIRESTORE_AVAILABLE = True
except ImportError:
    pass


class FirestoreMemoryBank:
    """Persistent Cross-Session Memory & Journey State Bank backed by Google Cloud Firestore."""

    def __init__(self, project_id: str | None = None) -> None:
        self.project_id = project_id or os.getenv("GOOGLE_CLOUD_PROJECT", "").strip()
        self.db: Any = None
        if FIRESTORE_AVAILABLE and self.project_id:
            try:
                self.db = firestore.Client(project=self.project_id)
                logger.info(f"Initialized Google Cloud Firestore Memory Bank for project: {self.project_id}")
            except Exception as e:
                logger.warning(f"Firestore initialization skipped: {e}")

    @property
    def is_active(self) -> bool:
        return self.db is not None

    def save_journey_state(self, session_id: str, payload: dict[str, Any]) -> None:
        if not self.is_active or not session_id:
            return
        try:
            doc_ref = self.db.collection("vidyut_journeys").document(session_id)
            doc_ref.set(payload, merge=True)
            logger.debug(f"Saved journey state in Firestore for session: {session_id}")
        except Exception as ex:
            logger.warning(f"Failed to persist journey state to Firestore: {ex}")

    def get_journey_state(self, session_id: str) -> dict[str, Any] | None:
        if not self.is_active or not session_id:
            return None
        try:
            doc_ref = self.db.collection("vidyut_journeys").document(session_id)
            snapshot = doc_ref.get()
            if snapshot.exists:
                return snapshot.to_dict()
        except Exception as ex:
            logger.warning(f"Failed to read journey state from Firestore: {ex}")
        return None


firestore_memory = FirestoreMemoryBank()
