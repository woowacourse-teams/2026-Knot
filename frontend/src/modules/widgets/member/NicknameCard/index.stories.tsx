import { AUTH_NICKNAME_API_PATH } from "@api/fetch/api/v1/auth/nickname";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";
import { userEvent, within } from "storybook/test";

import NicknameCard from ".";

const NICKNAME = "노티드";

/** 닉네임 입력창에 글자를 쳐요. */
const typeNickname = async (canvasElement: HTMLElement, nickname: string) => {
  const canvas = within(canvasElement);

  await userEvent.type(
    canvas.getByRole("textbox", { name: "닉네임" }),
    nickname,
  );

  return canvas;
};

/** 닉네임을 치고 확인을 눌러요. */
const submitNickname = async (canvasElement: HTMLElement) => {
  const canvas = await typeNickname(canvasElement, NICKNAME);

  await userEvent.click(canvas.getByRole("button", { name: "확인" }));
};

/** 닉네임 등록 요청이 이 상태 코드로 실패하게 해요. */
const failSubmitWith = (status: number) =>
  http.post(
    `*${AUTH_NICKNAME_API_PATH}`,
    () => new HttpResponse(null, { status }),
  );

/**
 * 닉네임을 입력받아 회원가입을 마치는 카드예요. GitHub 로그인을 마친 신규 사용자가 도착하는 온보딩 화면(`/onboarding`)에 놓여요.
 *
 * **동작 규칙**
 * - 닉네임을 등록해야 회원가입이 끝나고 서버가 접근 토큰을 발급해요. 등록하면 가입 완료 화면으로 옮겨 가요.
 * - 닉네임은 20자까지 한글·영어와 `(`, `)`, `-`만 쓸 수 있어요. 21자째는 입력되지 않아요.
 * - 글자를 칠 때마다 형식을 검사해 입력창 아래에 바로 알려요. 공백은 다른 금지 문자보다 먼저, 따로 알려요.
 * - 한글을 조합하는 중(「ㄷ」→「도」)에는 에러가 깜빡이지 않도록 자모도 통과시켜요.
 * - 비어 있거나 형식이 틀리면 「확인」을 누를 수 없어요. 누르면 등록이 끝날 때까지 버튼이 로딩으로 잠겨요.
 * - 등록이 실패하면 형식 에러와 같은 자리에 이유를 띄우고, 입력창으로 커서를 되돌려 바로 고칠 수 있게 해요. 값을 고치면 그 문구는 지워요.
 * - 로그인이 풀려 등록할 수 없으면 문구 대신 로그인 화면으로 돌려보내요.
 */
const meta = {
  title: "Member/NicknameCard",
  component: NicknameCard,
  parameters: {
    layout: "centered",
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=422-390",
    },
  },
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[PATH_ROUTE.ONBOARDING]}>
        <Routes>
          <Route
            path={PATH_ROUTE.ONBOARDING}
            element={
              // 카드가 최대 너비까지 펼쳐지도록 자리를 줘요
              <div style={{ width: "28.5rem" }}>
                <Story />
              </div>
            }
          />
          <Route path="*" element={<p>다음 화면으로 이동했어요.</p>} />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof NicknameCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 온보딩 화면에 처음 들어온 상태예요. 아직 입력이 없어 「확인」을 누를 수 없어요. */
export const Default: Story = {};

/** 쓸 수 있는 닉네임을 적은 상태예요. 「확인」을 누를 수 있게 돼요. */
export const Filled: Story = {
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-628",
    },
  },
  play: async ({ canvasElement }) => {
    await typeNickname(canvasElement, NICKNAME);
  },
};

/** 닉네임에 공백을 넣었을 때예요. 다른 금지 문자보다 먼저 공백을 알려요. */
export const WhitespaceError: Story = {
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-1237",
    },
  },
  play: async ({ canvasElement }) => {
    await typeNickname(canvasElement, "노 티드");
  },
};

/** 한글·영어와 `(`, `)`, `-` 밖의 문자를 넣었을 때예요. 예: 느낌표, 숫자 */
export const InvalidCharacterError: Story = {
  parameters: {
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-1237",
    },
  },
  play: async ({ canvasElement }) => {
    await typeNickname(canvasElement, "노티드!");
  },
};

/** 「확인」을 누른 뒤 등록을 기다리는 중이에요. 버튼이 로딩으로 잠겨 두 번 눌리지 않아요. */
export const Submitting: Story = {
  parameters: {
    msw: {
      handlers: {
        nickname: http.post(`*${AUTH_NICKNAME_API_PATH}`, async () => {
          await delay("infinite");
          return new HttpResponse(null, { status: 200 });
        }),
      },
    },
  },
  play: async ({ canvasElement }) => {
    await submitNickname(canvasElement);
  },
};

/** 보안 확인(CSRF)에 실패해 등록하지 못했을 때예요. 새로고침을 안내해요. */
export const SubmitForbidden: Story = {
  parameters: {
    msw: { handlers: { nickname: failSubmitWith(403) } },
  },
  play: async ({ canvasElement }) => {
    await submitNickname(canvasElement);
  },
};

/** 서버 오류 등으로 등록하지 못했을 때예요. 잠시 후 다시 시도하도록 안내해요. */
export const SubmitFailed: Story = {
  parameters: {
    msw: { handlers: { nickname: failSubmitWith(500) } },
  },
  play: async ({ canvasElement }) => {
    await submitNickname(canvasElement);
  },
};
