import styled from "@emotion/styled";
import type { Meta, StoryObj } from "@storybook/react-webpack5";

import GithubIcon from "@/assets/icons/github.svg";

import Row from ".";

/**
 * 자식을 가로로 나열하는 레이아웃이에요. 색·모양은 없고 자식의 위치만 잡아요.
 *
 * 방향만 다를 뿐 `Shared/Layout/Stack`과 동작이 같아요. 다만 방향이 바뀌므로
 * `align`은 **세로** 정렬, `justify`는 **가로** 정렬이 됩니다.
 * props와 `gap`·`Spacing` 사용 기준은 `Shared/Layout/Stack` 문서를 참고해요.
 */
const meta = {
  title: "Shared/Layout/Row",
  component: Row,
  args: {
    align: "center",
    justify: "start",
    gap: 0.5,
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
      <Row {...args}>
        <GithubIcon />
        <span>GitHub으로 시작하기</span>
      </Row>
    </Frame>
  ),
} satisfies Meta<typeof Row>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 아이콘과 글자를 가로로 붙일 때 써요. 예: 「GitHub으로 시작하기」 */
export const Default: Story = {};

/** 첫 자식은 왼쪽, 마지막 자식은 오른쪽 끝에 붙일 때 써요. 예: 제목과 「모두 보기」 */
export const JustifyBetween: Story = {
  args: { justify: "between" },
  render: (args) => (
    <Frame>
      <Row {...args}>
        <span>최근 문서</span>
        <span>모두 보기</span>
      </Row>
    </Frame>
  ),
};

const Frame = styled.div`
  width: 20rem;
  padding: 1rem;
  border: 1px dashed ${({ theme }) => theme.neutral[200]};
  color: ${({ theme }) => theme.neutral[800]};
`;
