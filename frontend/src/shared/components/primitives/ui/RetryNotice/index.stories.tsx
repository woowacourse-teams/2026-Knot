import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { fn } from "storybook/test";

import RetryNotice from ".";

/**
 * 잠깐의 실패를 알리고 다시 시도하게 하는 안내예요. 화면이나 구획을 그리는 데 필요한 요청이 실패했을 때 그 자리에 씁니다.
 * 예: 로그인 상태를 확인하지 못했을 때
 *
 * - 네트워크나 서버 사정처럼 다시 시도하면 될 수도 있는 실패에 써요.
 * - 인증이 풀린 401처럼 다시 시도해도 결과가 같은 실패에는 쓰지 않고 로그인 화면으로 보내요.
 * - 나타나자마자 스크린리더가 문구를 읽어요.
 */
const meta = {
  title: "Shared/RetryNotice",
  component: RetryNotice,
  args: {
    message: "로그인 상태를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.",
    onRetry: fn(),
  },
} satisfies Meta<typeof RetryNotice>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 요청이 실패했을 때예요. 예: 로그인 상태 확인 실패 */
export const Default: Story = {};
