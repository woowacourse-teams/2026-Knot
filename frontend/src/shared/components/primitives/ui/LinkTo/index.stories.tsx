import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { MemoryRouter } from "react-router";

import LinkTo from ".";

/**
 * 링크 이동만 맡는 컴포넌트예요. 모양이 없어서 카드·버튼 등 어떤 UI든 감싸 링크로 만들 수 있어요.
 * 예: 탐색 화면에서 근거 문서 카드를 눌러 노션 문서로 이동
 *
 * **이동 방식**
 * - `href`를 보고 이동 방식을 스스로 정해요.
 * - 앱 안의 경로(`/workspace` 등)면 페이지를 새로 불러오지 않고 앱 안에서 화면만 바꿔요.
 * - 외부 주소(`https:`, `mailto:` 등)면 새 탭에서 열어요.
 *
 * **쓰는 법**
 * - 새 탭 여부처럼 기본값을 바꾸고 싶으면 `target`을 직접 넘겨 덮어써요.
 * - 모양이 필요하면 `styled(LinkTo)`로 감싸요.
 *
 * ```tsx
 * <LinkTo href="https://www.notion.so/page">
 *   <SearchEvidenceCard {...evidence} />
 * </LinkTo>
 * ```
 */
const meta = {
  title: "Shared/LinkTo",
  component: LinkTo,
  args: {
    href: "/workspace",
    children: "워크스페이스로 이동",
  },
  // 앱 안 경로는 라우터 안에서만 그려져요
  decorators: [
    (Story) => (
      <MemoryRouter>
        <Story />
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof LinkTo>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 앱 안의 경로예요. 같은 탭에서 화면만 바뀌어요. */
export const Internal: Story = {};

/** 외부 주소예요. 새 탭에서 열려요. 예: 근거 문서의 노션 페이지 */
export const External: Story = {
  args: { href: "https://www.notion.so", children: "노션에서 열기" },
};
