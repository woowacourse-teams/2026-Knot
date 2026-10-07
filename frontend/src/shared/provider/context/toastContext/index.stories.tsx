import styled from "@emotion/styled";
import type { Meta, StoryObj } from "@storybook/react-webpack5";

import Button from "@primitives/ui/Button";

import { ToastProvider, useToast } from ".";

const SUCCESS_MESSAGE = "문서를 모두 확인했어요";
const CAUTION_MESSAGE =
  "연결이 끊겨 녹음이 끝났어요. 여기까지 문서로 정리하고 있어요";
const ERROR_MESSAGE = "질문을 보내지 못했어요. 잠시 후 다시 시도해 주세요.";
const COPY_FAILED_MESSAGE = "복사하지 못했어요. 다시 시도해 주세요.";
const NOTHING_TO_DOCUMENT_MESSAGE = "문서로 만들 내용이 없었어요";
const WORKSPACE_MOVED_MESSAGE = "워크스페이스를 옮겼어요";

const FOUR_IN_A_ROW_INTERVAL_MS = 400;
const THREE_QUICKLY_INTERVAL_MS = 100;

/**
 * 어느 화면에서든 `useToast().show`로 토스트를 띄우게 해 주는 프로바이더예요. 앱 최상단에 한 번 깔아요.
 *
 * **쌓이는 규칙**
 * - 가장 최근 토스트가 맨 아래(독에 가장 가까이)에 쌓이고, 떠 있는 토스트는 최대 3개예요.
 *   4번째가 오면 가장 오래된 토스트가 모션 없이 바로 빠져요.
 * - 종류·문구가 모두 같은 토스트가 떠 있으면 새로 쌓지 않고 떠 있는 시간만 처음부터 다시 세요.
 * - 사라지는 중인 토스트는 이미 끝난 알림이라 3개에 세지 않아요. 그동안 같은 토스트를 띄우면 새로 쌓여요.
 *   그래서 잠깐 최대 4개(떠 있는 3개 + 사라지는 1개)가 보일 수 있어요.
 *
 * **떠 있는 시간과 모션**
 * - 정상 5초, 주의·오류 8초 동안 떠 있어요. 주의·오류는 읽고 대처할 시간이 더 필요해서예요.
 * - 나타날 때 0.26초 동안 아래에서 살짝 떠오르며 칸이 펼쳐지고, 사라질 때 0.25초 동안 흐려지며 칸이 접혀요.
 *   여러 개가 잇달아 떠도 위의 토스트가 멈칫하지 않고 한 덩어리로 밀려 올라가고, 칸이 접히는 만큼 부드럽게 내려와요.
 *
 * 토스트 목록을 화면 아래 가운데에 둔 것은 이 스토리의 배치예요. 프로바이더는 아직 위치를 정하지 않아요.
 */
const meta = {
  title: "Shared/ToastProvider",
  component: ToastProvider,
  parameters: {
    layout: "fullscreen",
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1783-13216",
    },
  },
  argTypes: {
    children: { control: false },
  },
  args: {
    children: null,
  },
  decorators: [
    (Story) => (
      <StoryScreen>
        <Story />
      </StoryScreen>
    ),
  ],
} satisfies Meta<typeof ToastProvider>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 버튼으로 토스트를 띄워 쌓이는 규칙과 나타나고 사라지는 모션을 직접 확인해요 */
export const Playground: Story = {
  render: () => (
    <ToastProvider>
      <ToastPlayground />
    </ToastProvider>
  ),
};

function ToastPlayground() {
  const { show } = useToast();

  const showInARow = (
    toasts: Parameters<typeof show>[0][],
    intervalMs: number,
  ) => {
    toasts.forEach((toast, index) => {
      setTimeout(() => show(toast), index * intervalMs);
    });
  };

  return (
    <ButtonContainer>
      <Button
        size="sm"
        variant="accent"
        onClick={() => show({ variant: "success", message: SUCCESS_MESSAGE })}
      >
        정상 띄우기
      </Button>
      <Button
        size="sm"
        variant="outline"
        onClick={() => show({ variant: "caution", message: CAUTION_MESSAGE })}
      >
        주의 띄우기
      </Button>
      <Button
        size="sm"
        variant="danger"
        onClick={() => show({ variant: "error", message: ERROR_MESSAGE })}
      >
        오류 띄우기
      </Button>
      <Button
        size="sm"
        variant="outline"
        onClick={() => show({ variant: "success", message: SUCCESS_MESSAGE })}
      >
        같은 토스트 다시 띄우기
      </Button>
      <Button
        size="sm"
        variant="outline"
        onClick={() =>
          showInARow(
            [
              { variant: "success", message: SUCCESS_MESSAGE },
              { variant: "error", message: COPY_FAILED_MESSAGE },
              { variant: "caution", message: NOTHING_TO_DOCUMENT_MESSAGE },
              { variant: "success", message: WORKSPACE_MOVED_MESSAGE },
            ],
            FOUR_IN_A_ROW_INTERVAL_MS,
          )
        }
      >
        4개 연달아 띄우기
      </Button>
      <Button
        size="sm"
        variant="outline"
        onClick={() =>
          showInARow(
            [
              { variant: "error", message: COPY_FAILED_MESSAGE },
              { variant: "caution", message: NOTHING_TO_DOCUMENT_MESSAGE },
              { variant: "success", message: WORKSPACE_MOVED_MESSAGE },
            ],
            THREE_QUICKLY_INTERVAL_MS,
          )
        }
      >
        서로 다른 문구 3개 빠르게
      </Button>
    </ButtonContainer>
  );
}

// 프로바이더가 버튼 뒤에 토스트 목록을 그리므로, 둘을 위아래 끝으로 벌려 목록을 아래 가운데에 둬요.
// 높이를 화면(100vh)에 맞추면 낮은 캔버스나 Docs 페이지에서 목록이 보이는 영역 밖으로 밀려서,
// 토스트 4개(떠 있는 3개 + 사라지는 1개)가 들어갈 만큼만 고정해요.
// Docs의 스토리 블록은 transform이 걸려 있어 position: fixed로는 화면 아래에 고정되지 않아요
const StoryScreen = styled.div`
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 22rem; /* 352px */
  padding: 1.5rem 1.5rem 2.5rem;
`;

const ButtonContainer = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem; /* 8px */
`;
