import type { Meta, StoryObj } from "@storybook/react-webpack5";

import SearchEvidenceCard from ".";

/**
 * 답변의 근거가 된 문서 하나를 보여주는 카드예요. 탐색 화면 오른쪽 「찾은 기록」 패널에 써요.
 *
 * - 첫 줄에 문서 제목, 둘째 줄에 출처(근거가 나온 곳의 종류, v2는 `문서`만)와 문서 날짜를 보여줘요.
 * - 제목은 두 줄까지 보이고 넘치면 말줄임돼요.
 * - 출처와 날짜는 아직 API에 없어 BE에 요청할 예정이에요. 지금은 예시 값으로 그려요.
 * - 카드는 라우팅을 모르고 모양만 그려요. 눌러서 문서로 가는 링크는 쓰는 곳(찾은 기록 패널)이 `LinkTo`로 감싸요.
 */
const meta = {
  title: "Shared/SearchEvidenceCard",
  component: SearchEvidenceCard,
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2098-29280",
    },
  },
  args: {
    title: "회원 탈퇴 정책",
    sourceType: "문서",
    createdAt: "2026-09-15T01:00:00Z",
  },
  decorators: [
    (Story) => (
      // 찾은 기록 패널(400px)의 안쪽 폭이에요
      <div style={{ width: "22rem" }}>
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof SearchEvidenceCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 답변의 근거로 찾은 문서 하나예요. */
export const Default: Story = {};

/** 제목이 길 때예요. 두 줄까지 보이고 넘치면 말줄임돼요. */
export const LongTitle: Story = {
  args: {
    title:
      "2026 하반기 제품 로드맵 확정 및 회원 탈퇴 정책 개편 관련 논의 회의록과 후속 결정 사항 정리",
  },
};
