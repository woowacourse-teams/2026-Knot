import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { useArgs } from "storybook/preview-api";
import { fn } from "storybook/test";

import Stepper from ".";

/**
 * 여러 항목을 이전·다음으로 하나씩 넘기는 컨트롤이에요. 녹음 직후 확인 화면의 하단 바 안에서, 녹음으로 만든 문서를 하나씩 넘겨 볼 때 씁니다.
 * 화면에 단독으로 두지 않아요.
 *
 * - `현재 / 전체`를 1부터 세어 보여줘요.
 * - 첫 항목에서는 이전 버튼을, 마지막 항목에서는 다음 버튼을 막아요.
 * - 지금 순서는 쓰는 쪽이 들고 있고, 버튼을 누르면 `onPrev`·`onNext`로 알리기만 해요.
 * - 숫자마다 폭이 달라 넘길 때마다 흔들리지 않도록 모든 숫자를 같은 폭으로 그려요.
 *
 * **디자인 원본**: [Stepper/RecordingDocs](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1750-743)
 */
const meta = {
  title: "Shared/Stepper",
  component: Stepper,
  args: {
    current: 1,
    total: 3,
    onPrev: fn(),
    onNext: fn(),
  },
  // 리뷰어가 버튼을 눌러 넘겨 볼 수 있게, 바뀐 순서를 스토리 args에 다시 넣어요
  render: function Render(args) {
    const [, updateArgs] = useArgs<typeof args>();
    const handlePrev = () => {
      args.onPrev();
      updateArgs({ current: args.current - 1 });
    };
    const handleNext = () => {
      args.onNext();
      updateArgs({ current: args.current + 1 });
    };

    return <Stepper {...args} onPrev={handlePrev} onNext={handleNext} />;
  },
} satisfies Meta<typeof Stepper>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 첫 항목이에요. 이전 버튼이 막혀요. */
export const First: Story = {};

/** 가운데 항목이에요. 양쪽 버튼을 모두 누를 수 있어요. */
export const Middle: Story = {
  args: { current: 2 },
};

/** 마지막 항목이에요. 다음 버튼이 막혀요. */
export const Last: Story = {
  args: { current: 3 },
};
