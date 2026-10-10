interface DocumentTopic {
  /** 폴더 이름 */
  topic: string;
  /** 서버가 센 그 주제의 전체 문서 수 */
  documentCount: number;
}

interface TopicDocument {
  /** 문서가 속한 폴더 이름 */
  topic: string;
}

interface GroupDocumentsByTopicParams<TDocument extends TopicDocument> {
  /** 서버가 준 주제 폴더 목록 */
  topics: DocumentTopic[];
  /** 묶을 문서. 서버가 준 순서(최신순)대로 넘겨요 */
  documents: TDocument[];
}

// 서버가 주는 주제 순서는 명세에 없어, 화면에서 한국어 사전 순서로 다시 놓아요.
// 한국어 사용자가 대상이라 한글 이름을 영문 이름보다 위에 두고, 영문은 대소문자를 가리지 않아요
const topicCollator = new Intl.Collator("ko");

/**
 * 문서를 주제 폴더별로 묶어요.
 *
 * - 폴더는 이름순으로 놓아요. 한글 이름의 폴더가 영문 이름의 폴더보다 위에 와요.
 * - 폴더 안의 문서는 받은 순서를 그대로 지켜요. 최신순으로 받았으면 폴더 안에서도 최신순이에요.
 * - 폴더의 문서 수는 받은 문서를 세지 않고 서버가 준 `documentCount`를 써요.
 *   문서를 전부 받지 못했을 때도 폴더에 전체 수를 보여 주기 위해서예요.
 * - 주제 목록에 없는 주제의 문서도 빠뜨리지 않아요. 그 주제의 폴더를 만들고, 문서 수는 받은 문서를 세요.
 * - 받은 문서가 하나도 없는 주제도 폴더로 남겨요.
 *
 * @example
 * groupDocumentsByTopic({
 *   topics: [{ topic: "회원", documentCount: 2 }],
 *   documents: [
 *     { id: 2, topic: "회원" },
 *     { id: 1, topic: "회원" },
 *   ],
 * });
 * // [{ topic: "회원", documentCount: 2, documents: [{ id: 2, … }, { id: 1, … }] }]
 */
export const groupDocumentsByTopic = <TDocument extends TopicDocument>({
  topics,
  documents,
}: GroupDocumentsByTopicParams<TDocument>) => {
  const countByTopic = new Map(
    topics.map(({ topic, documentCount }) => [topic, documentCount]),
  );
  const documentsByTopic = new Map<string, TDocument[]>();

  documents.forEach((document) => {
    const topicDocuments = documentsByTopic.get(document.topic);

    if (topicDocuments === undefined) {
      documentsByTopic.set(document.topic, [document]);
      return;
    }

    topicDocuments.push(document);
  });

  const topicNames = new Set([
    ...countByTopic.keys(),
    ...documentsByTopic.keys(),
  ]);

  return [...topicNames].sort(topicCollator.compare).map((topic) => {
    const topicDocuments = documentsByTopic.get(topic) ?? [];

    return {
      topic,
      documentCount: countByTopic.get(topic) ?? topicDocuments.length,
      documents: topicDocuments,
    };
  });
};
