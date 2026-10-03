import type { Meta, StoryObj } from "@storybook/react-webpack5";

import GithubIcon from "@/assets/icons/github.svg";

import Button, { type ButtonVariant } from ".";

/**
 * 액션을 실행하는 버튼이에요. 화면에서 사용자가 「무언가를 실행하는」 곳에 씁니다.
 *
 * **어떤 모양을 고르나**
 * - `filled` : 화면의 주요 동작 하나. 예: 「워크스페이스 만들기」
 * - `outline` : 주요 동작 옆의 보조 동작. 예: 「취소」
 * - `accent` : 방금 끝난 일을 알릴 때. 예: 초대 코드를 복사한 뒤의 「복사됨」
 * - `danger` : 되돌릴 수 없는 동작. 예: 워크스페이스 나가기
 *
 * **동작 규칙**
 * - 상태는 prop으로 직접 넘기지 않고 `isLoading`·`disabled`로 정해요. 둘 다 넘기면 로딩 모양이 이깁니다.
 * - 로딩 중에는 누를 수 없어요. 연속 클릭으로 같은 요청이 두 번 가는 것을 막습니다.
 * - 로딩 중에도 라벨이 자리를 지켜 버튼 너비가 변하지 않아요. 라벨은 보이지 않게만 감춰서 스크린리더는 계속 버튼 이름을 읽습니다.
 * - 높이는 padding으로 만들어요. 사용자가 글꼴을 키워도 글자가 잘리지 않습니다.
 *
 * **디자인 원본**: [Button/CTA/L](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=422-440) ·
 * [M](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=511-284) ·
 * [S](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=484-4907) ·
 * [복사됨(accent)](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=484-4926)
 */
const meta = {
  title: "Shared/Button",
  component: Button,
  args: {
    children: "워크스페이스 만들기",
    size: "md",
    variant: "filled",
    isLoading: false,
    isFullWidth: false,
    disabled: false,
  },
  argTypes: {
    size: { control: "inline-radio", options: ["lg", "md", "sm"] },
    variant: {
      control: "inline-radio",
      options: ["filled", "outline", "accent", "danger"],
    },
  },
} satisfies Meta<typeof Button>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 가장 많이 쓰는 모양이에요. 화면의 주요 동작 하나에만 씁니다. */
export const Filled: Story = {};

/** 주요 동작 옆의 보조 동작에 써요. 예: 「취소」 */
export const Outline: Story = {
  args: { variant: "outline", children: "취소" },
};

/** 방금 끝난 일을 알릴 때 써요. 예: 초대 코드를 복사한 뒤의 「복사됨」 */
export const Accent: Story = {
  args: { variant: "accent", children: "복사됨" },
};

/** 되돌릴 수 없는 동작에 써요. 예: 워크스페이스 나가기 */
export const Danger: Story = {
  args: { variant: "danger", children: "나가기" },
};

/**
 * 요청을 처리하는 중이에요. 스피너가 라벨 자리에 뜨고 누를 수 없어요.
 * `disabled`와 함께 넘겨도 로딩 모양이 이깁니다. 처리 중인 버튼을 회색으로 그리면 아무 일도 없는 것처럼 보이기 때문이에요.
 */
export const Loading: Story = {
  args: { isLoading: true },
};

/** 아직 누를 수 없는 상태예요. 예: 필수 입력이 비어 있을 때 */
export const Disabled: Story = {
  args: { disabled: true },
};

/** 세 가지 크기를 나란히 비교해요. */
export const Sizes: Story = {
  render: (args) => (
    <div style={{ display: "flex", gap: "1rem", alignItems: "center" }}>
      <Button {...args} size="lg">
        큰 버튼
      </Button>
      <Button {...args} size="md">
        중간 버튼
      </Button>
      <Button {...args} size="sm">
        작은 버튼
      </Button>
    </div>
  ),
};

/** 아이콘이 붙은 버튼이에요. 아이콘은 크기별로 정해진 크기로 맞춰져요. 예: 로그인 화면의 「GitHub으로 시작하기」 */
export const WithIcon: Story = {
  args: {
    size: "lg",
    children: (
      <>
        <GithubIcon />
        GitHub으로 시작하기
      </>
    ),
  },
};

/** 카드나 화면 안의 단독 CTA처럼 가로를 꽉 채울 때 써요. 예: 워크스페이스 초대 카드의 「다음」 */
export const FullWidth: Story = {
  args: { size: "lg", isFullWidth: true, children: "다음" },
  decorators: [
    (Story) => (
      <div style={{ width: "22.5rem" }}>
        <Story />
      </div>
    ),
  ],
};

const VARIANTS: ButtonVariant[] = ["filled", "outline", "accent", "danger"];

/** variant와 상태를 한 화면에 모은 표예요. 디자인 시안과 나란히 놓고 비교할 때 써요. */
export const AllStates: Story = {
  render: (args) => (
    <table style={{ borderSpacing: "1rem" }}>
      <thead>
        <tr>
          <th />
          <th>active</th>
          <th>loading</th>
          <th>inactive</th>
        </tr>
      </thead>
      <tbody>
        {VARIANTS.map((variant) => (
          <tr key={variant}>
            <th>{variant}</th>
            <td>
              <Button {...args} variant={variant} />
            </td>
            <td>
              <Button {...args} variant={variant} isLoading />
            </td>
            <td>
              <Button {...args} variant={variant} disabled />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  ),
  args: { children: "확인" },
};
