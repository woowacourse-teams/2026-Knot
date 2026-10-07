package com.knot.backend.document.infrastructure;

import com.knot.backend.document.domain.DocumentConfirmation;
import com.knot.backend.document.domain.DocumentConfirmationId;
import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentConfirmationJpaRepository extends JpaRepository<DocumentConfirmation, DocumentConfirmationId> {

}
