import type { Meta, StoryObj } from "@storybook/react-webpack5";
import type { ChangeEvent } from "react";
import { useArgs } from "storybook/preview-api";

import CountTextField from ".";

/**
 * 글자 수 카운터가 달린 입력 필드예요. 온보딩의 닉네임 입력처럼 길이 제한을 알려야 하는 입력에 씁니다.
 *
 * - 입력창 위 오른쪽에 `3/20`처럼 지금 글자 수와 최대 글자 수를 보여줘요.
 * - 에러 메시지는 `TextField`처럼 입력창 아래에 그려요. 카운터는 위, 메시지는 아래예요.
 *
 * **동작 규칙**
 * - `maxLength`를 입력창에도 그대로 넘겨서 최대 글자 수를 넘겨 입력할 수 없어요. 그래서 카운터가 분모를 넘는 일은 생기지 않아요.
 * - 입력창의 색과 메시지 규칙은 `Shared/TextField`와 같아요.
 */
const meta = {
  title: "Shared/CountTextField",
  component: CountTextField,
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-1193",
    },
  },
  args: {
    value: "",
    maxLength: 20,
    placeholder: "닉네임",
    "aria-label": "닉네임",
  },
  decorators: [
    (Story) => (
      <div style={{ width: "22.5rem" }}>
        <Story />
      </div>
    ),
  ],
  // 리뷰어가 직접 입력해 볼 수 있게, 입력한 값을 스토리 args에 다시 넣어요
  render: function Render(args) {
    const [, updateArgs] = useArgs<typeof args>();
    const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
      updateArgs({ value: event.target.value });
    };

    return <CountTextField {...args} onChange={handleChange} />;
  },
} satisfies Meta<typeof CountTextField>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 아직 입력하지 않은 처음 모양이에요. 카운터가 `0/20`이에요. */
export const Empty: Story = {};

/** 값이 들어 있을 때예요. 카운터가 글자 수를 따라가요. */
export const Filled: Story = {
  args: { value: "흑곰" },
};

/** 최대 글자 수까지 채웠을 때예요. 더 입력해도 들어가지 않아요. */
export const MaxLength: Story = {
  args: { value: "가나다라마바사아자차카타파하가나다라마바" },
};

/** 유효하지 않은 값이에요. 입력창이 에러 색이 되고 아래에 메시지가 생겨요. 예: 닉네임에 공백을 넣었을 때 */
export const WithError: Story = {
  args: { value: "흑 곰", errorMessage: "공백은 사용할 수 없어요." },
};
