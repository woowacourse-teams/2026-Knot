import type { Meta, StoryObj } from "@storybook/react-webpack5";

import NotionIcon from "@/assets/icons/notion.svg";

import SearchEvidenceCard from ".";

/**
 * AI 탐색 답변의 근거가 된 문서를 보여주는 카드예요. 탐색 화면의 「찾은 문서」 목록에 씁니다.
 *
 * - 문서 출처 아이콘(예: 노션), 문서 제목, 문서 위치를 보여줘요.
 * - 제목은 두 줄까지 보이고 넘치면 말줄임돼요.
 * - 마우스를 올리면 오른쪽 위에 「외부에서 열기」 아이콘이 나타나요.
 * - 이동은 카드가 아니라 감싸는 `LinkTo`가 맡아요.
 */
const meta = {
  title: "Shared/SearchEvidenceCard",
  component: SearchEvidenceCard,
  parameters: {
    design: [
      {
        name: "720-563",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=720-563",
      },
      {
        name: "720-573",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=720-573",
      },
      {
        name: "720-584",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=720-584",
      },
      {
        name: "988-7115",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=988-7115",
      },
    ],
  },
  args: {
    title: "2026 H2 로드맵",
    documentPath: "제품/로드맵",
    evidenceSourceIcon: <NotionIcon />,
  },
  decorators: [
    (Story) => (
      <div style={{ width: "30rem" }}>
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof SearchEvidenceCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 노션 문서를 근거로 찾았을 때예요. 마우스를 올려 「외부에서 열기」 아이콘을 확인해 보세요. */
export const Default: Story = {};

/** 제목이 길 때예요. 두 줄까지 보이고 넘치면 말줄임돼요. */
export const LongTitle: Story = {
  args: {
    title:
      "2026 H2 제품 로드맵 확정 및 DB 기술 선정 관련 논의 회의록 관련 논의 회의록 관련 논의 회의록논의 회의록",
    documentPath: "제품/스펙",
  },
};
