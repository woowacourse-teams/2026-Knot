import styled from "@emotion/styled";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { useRef, useState } from "react";

import Button from "@primitives/ui/Button";

import FadeCollapse, { FADE_COLLAPSE_LEAVE_MS } from ".";

const DOCUMENT_TITLES = ["회의록", "기획 문서", "API 명세", "회고록"];

interface ShownItem {
  id: number;
  title: string;
  isLeaving: boolean;
}

/**
 * 목록에 요소가 들어오고 나갈 때, 요소가 차지하는 높이를 함께 펼치고 접는 애니메이션이에요.
 * 높이가 같이 움직여서 위아래 요소가 순간 이동하지 않고 밀리거나 내려와요. 예: 토스트가 쌓이고 사라질 때
 *
 * **움직임**
 * - 나타날 때 0.26초 동안 높이가 펼쳐지며 살짝 아래에서 떠오르고 선명해져요.
 *   천천히 출발해서, 여러 개가 잇달아 들어와도 위 요소가 멈칫하지 않고 한 덩어리로 밀려 올라가요.
 * - `isLeaving`이 `true`가 되면 0.25초 동안 흐려지고 아주 조금 작아지며 높이가 접혀요.
 *   튀거나 떨어지는 움직임은 없어요.
 *
 * **쓰는 쪽이 할 일**
 * - 접히는 동안에도 요소를 그려 두고, `FADE_COLLAPSE_LEAVE_MS`가 지난 뒤 목록에서 빼요.
 * - 요소 사이 간격은 목록의 `gap`이 아니라 자식 안쪽 여백으로 둬야 접힐 때 간격도 함께 사라져요.
 */
const meta = {
  title: "Shared/Animation/FadeCollapse",
  component: FadeCollapse,
  argTypes: {
    isLeaving: { control: false },
    children: { control: false },
  },
  args: {
    isLeaving: false,
    children: null,
  },
} satisfies Meta<typeof FadeCollapse>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 버튼으로 문서를 더하고 빼며 나타나고 사라지는 움직임을 확인해요 */
export const Playground: Story = {
  render: () => <FadeCollapsePlayground />,
};

function FadeCollapsePlayground() {
  const [items, setItems] = useState<ShownItem[]>([]);
  const nextIdRef = useRef(0);

  const handleAdd = () => {
    const id = nextIdRef.current++;
    const title = DOCUMENT_TITLES[id % DOCUMENT_TITLES.length];

    setItems((current) => [...current, { id, title, isLeaving: false }]);
  };

  const handleRemoveFirst = () => {
    const target = items.find((item) => !item.isLeaving);

    if (target === undefined) return;

    setItems((current) =>
      current.map((item) =>
        item.id === target.id ? { ...item, isLeaving: true } : item,
      ),
    );
    setTimeout(() => {
      setItems((current) => current.filter((item) => item.id !== target.id));
    }, FADE_COLLAPSE_LEAVE_MS);
  };

  return (
    <Container>
      <ButtonContainer>
        <Button size="sm" variant="accent" onClick={handleAdd}>
          문서 더하기
        </Button>
        <Button size="sm" variant="outline" onClick={handleRemoveFirst}>
          맨 위 문서 빼기
        </Button>
      </ButtonContainer>
      <div>
        {items.map((item) => (
          <FadeCollapse key={item.id} isLeaving={item.isLeaving}>
            <ItemGapWrapper>
              <Item>{item.title}</Item>
            </ItemGapWrapper>
          </FadeCollapse>
        ))}
      </div>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1rem; /* 16px */
  width: 20rem; /* 320px */
`;

const ButtonContainer = styled.div`
  display: flex;
  gap: 0.5rem; /* 8px */
`;

const ItemGapWrapper = styled.div`
  padding-bottom: 0.5rem; /* 8px */
`;

const Item = styled.div`
  padding: 0.75rem 1rem; /* 12px 16px */
  border-radius: 0.75rem; /* 12px */
  background-color: ${({ theme }) => theme.neutral[100]};
  ${({ theme }) => theme.text.label01};
`;
