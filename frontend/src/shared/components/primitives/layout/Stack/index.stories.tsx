import styled from "@emotion/styled";
import Spacing from "@primitives/layout/Spacing";
import type { Meta, StoryObj } from "@storybook/react-webpack5";

import Stack from ".";

/**
 * 자식을 세로로 쌓는 레이아웃이에요. 색·모양은 없고 자식의 위치만 잡아요.
 *
 * `display: flex`와 `flex-direction: column`을 매번 적는 대신 씁니다. 가로로 나열할 때는 `Shared/Layout/Row`를 써요.
 * `main`, `section`, `ul`처럼 의미가 있는 태그가 필요하면 `as`로 바꿔 그려요.
 *
 * **`gap`과 `Spacing` 중 무엇을 쓰나**
 * - `gap` : 자식들이 **모두 같은 간격**으로 나열될 때. 예: 리스트, 카드 목록, 폼 필드 여러 개.
 *   자식이 조건부로 사라지면 간격도 함께 사라져요.
 * - `Spacing` : 간격이 **자리마다 다를 때**. 여러 개의 `gap`을 만들려고 그룹 `div`를 겹겹이 만드는 상황이면
 *   `Spacing`이 읽기 쉬워요. 예: 로그인 화면의 로고·제목·버튼
 *
 * **간격 값**
 * - 숫자를 넘기면 `rem`으로 붙고, 문자열은 `16px`처럼 단위까지 그대로 적용돼요.
 */
const meta = {
  title: "Shared/Layout/Stack",
  component: Stack,
  args: {
    align: "stretch",
    justify: "start",
    gap: 1,
  },
  argTypes: {
    align: {
      control: "inline-radio",
      options: ["start", "center", "end", "stretch"],
    },
    justify: {
      control: "inline-radio",
      options: ["start", "center", "end", "between"],
    },
    gap: { control: "number" },
  },
  render: (args) => (
    <Frame>
      <Stack {...args}>
        <Item>회의록</Item>
        <Item>기획 문서</Item>
        <Item>API 명세</Item>
      </Stack>
    </Frame>
  ),
} satisfies Meta<typeof Stack>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 같은 간격으로 쌓는 기본 모양이에요. 예: 문서 목록 */
export const Default: Story = {};

/** 자식을 가로 가운데에 모을 때 써요. 예: 로그인 화면의 로고와 버튼 */
export const AlignCenter: Story = {
  args: { align: "center" },
};

/** 높이가 정해진 칸에서 첫 자식은 위, 마지막 자식은 아래에 붙일 때 써요. */
export const JustifyBetween: Story = {
  args: { justify: "between" },
  render: (args) => (
    <Frame style={{ height: "16rem" }}>
      <Stack {...args} style={{ height: "100%" }}>
        <Item>회의록</Item>
        <Item>기획 문서</Item>
        <Item>API 명세</Item>
      </Stack>
    </Frame>
  ),
};

/** 간격을 피그마 px 값 그대로 옮길 때 문자열로 넘겨요. */
export const GapWithUnit: Story = {
  args: { gap: "16px" },
};

/** 간격이 자리마다 다르면 `gap` 대신 사이사이에 `Spacing`을 놓아요. */
export const WithSpacing: Story = {
  args: { align: "center", gap: undefined },
  render: (args) => (
    <Frame>
      <Stack {...args}>
        <Item>로고</Item>
        <Spacing size={2.5} />
        <Item>팀 문서화, 이제 기다리지 마세요</Item>
        <Spacing size={4.5} />
        <Item>GitHub으로 시작하기</Item>
      </Stack>
    </Frame>
  ),
};

const Frame = styled.div`
  width: 20rem;
  padding: 1rem;
  border: 1px dashed ${({ theme }) => theme.neutral[200]};
`;

const Item = styled.div`
  padding: 0.5rem 0.75rem;
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 0.5rem;
  background-color: ${({ theme }) => theme.neutral[0]};
  color: ${({ theme }) => theme.neutral[800]};
`;
