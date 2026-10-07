import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { fn } from "storybook/test";

import InlineError from ".";

/**
 * 화면 안 한 영역이나 한 동작만 실패했을 때, 그 내용이 있어야 할 자리에 남기는 안내예요.
 * 예: 문서 목록을 불러오지 못했을 때, 탐색 답변을 만들지 못했을 때
 *
 * - 실패한 자리에 왼쪽 정렬로 들어가고, 사용자가 다시 시도할 때까지 남아요.
 * - 같은 실패를 토스트로 또 알리지 않아요.
 * - 화면 전체가 막히는 실패(로그인 확인·첫 화면 준비)에는 가운데 정렬인 `RetryNotice`를 써요.
 * - 나타나자마자 스크린리더가 문구를 읽어요.
 */
const meta = {
  title: "Shared/InlineError",
  component: InlineError,
  args: {
    message: "목록을 불러오지 못했어요. 다시 시도해 주세요.",
    onRetry: fn(),
  },
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2243-32815",
    },
  },
} satisfies Meta<typeof InlineError>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 한 영역의 목록을 불러오지 못했을 때예요. 예: 문서 목록 불러오기 실패 */
export const Default: Story = {};

/** 탐색에서 답변을 만들지 못했을 때, 답변 자리에 들어가요 */
export const AnswerFailed: Story = {
  args: {
    message: "답변을 만들지 못했어요. 잠시 후 다시 시도해 주세요.",
  },
};
