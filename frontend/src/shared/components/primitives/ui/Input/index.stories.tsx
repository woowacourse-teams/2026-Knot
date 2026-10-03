import type { Meta, StoryObj } from "@storybook/react-webpack5";
import type { ChangeEvent } from "react";
import { useArgs } from "storybook/preview-api";

import Input, { type InputStatus, type InputVariant } from ".";

/**
 * 한 줄 텍스트 입력창이에요. 메시지 없이 입력창 모양만 필요할 때 씁니다. 에러·성공 메시지까지 함께 다루려면 `TextField`를 써요.
 *
 * **어떤 모양을 고르나** (`variant`)
 * - `text` : 일반 글 입력. 예: 워크스페이스 이름
 * - `code` : 가운데 정렬·넓은 자간의 코드 입력. 예: 6자리 초대 코드
 * - `copy` : 복사용 읽기 전용 링크. 예: 팀원 초대 링크. `readOnly`와 함께 써요
 *
 * **어떤 상태를 고르나** (`status`)
 * - `empty` : 아직 입력하지 않음
 * - `filled` : 값이 들어 있음
 * - `error` : 유효하지 않은 값
 * - `success` : 검증을 통과한 값. 예: 확인된 초대 코드
 *
 * **동작 규칙**
 * - 값이 비었는지·에러인지를 스스로 판단하지 않고 `status`로 받아 그리기만 해요. 판단은 값을 들고 있는 쪽이 해요. 그래서 직접 입력해도 색이 바뀌지 않으니 `status` 컨트롤로 바꿔 보세요.
 * - `status`는 색(배경·테두리)만, `variant`는 형태·글자만 정해서 둘을 자유롭게 섞을 수 있어요.
 * - `error`이면 `aria-invalid`가 붙어 스크린리더도 잘못된 값임을 읽어요.
 *
 * **디자인 원본**: [Field/TextField](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-596) ·
 * [입력 전](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-595) ·
 * [입력 중](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-597) ·
 * [입력 에러](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1325) ·
 * [Field/TextField/Code](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=443-910) ·
 * [인증 완료](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=664-552) ·
 * [Field/Copy](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=484-4925)
 */
const meta = {
  title: "Shared/Input",
  component: Input,
  args: {
    value: "",
    status: "empty",
    variant: "text",
    placeholder: "예시: knot",
    "aria-label": "워크스페이스 이름",
  },
  argTypes: {
    status: {
      control: "inline-radio",
      options: ["empty", "filled", "error", "success"],
    },
    variant: { control: "inline-radio", options: ["text", "code", "copy"] },
  },
  // 리뷰어가 직접 입력해 볼 수 있게, 입력한 값을 스토리 args에 다시 넣어요
  render: function Render(args) {
    const [, updateArgs] = useArgs<typeof args>();
    const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
      updateArgs({ value: event.target.value });
    };

    return <Input {...args} onChange={handleChange} />;
  },
} satisfies Meta<typeof Input>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 아직 입력하지 않은 처음 모양이에요. */
export const Empty: Story = {};

/** 값이 들어 있을 때예요. */
export const Filled: Story = {
  args: { value: "knot", status: "filled" },
};

/** 유효하지 않은 값이에요. 예: 워크스페이스 이름에 쓸 수 없는 문자를 넣었을 때 */
export const WithError: Story = {
  args: { value: "knot!", status: "error" },
};

/** 검증을 통과한 값이에요. 예: 확인된 초대 코드 */
export const Success: Story = {
  args: {
    variant: "code",
    value: "AB12CD",
    status: "success",
    "aria-label": "참여 코드",
  },
};

/** 코드 입력이에요. 가운데 정렬에 자간을 넓혀 한 글자씩 읽기 쉽게 해요. 예: 6자리 초대 코드 */
export const Code: Story = {
  args: {
    variant: "code",
    placeholder: "코드를 입력하세요",
    maxLength: 6,
    "aria-label": "참여 코드",
  },
};

/** 복사용 읽기 전용 링크예요. 예: 팀원 초대 화면의 초대 링크 */
export const Copy: Story = {
  args: {
    variant: "copy",
    status: "filled",
    value: `${window.location.origin}/invite/AB12CD`,
    readOnly: true,
    "aria-label": "초대 링크",
  },
};

const STATUSES: InputStatus[] = ["empty", "filled", "error", "success"];
const VARIANTS: InputVariant[] = ["text", "code", "copy"];

/** variant와 status를 한 화면에 모은 표예요. 디자인 시안과 나란히 놓고 비교할 때 써요. */
export const AllStates: Story = {
  render: (args) => (
    <table style={{ borderSpacing: "1rem" }}>
      <thead>
        <tr>
          <th />
          {STATUSES.map((status) => (
            <th key={status}>{status}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {VARIANTS.map((variant) => (
          <tr key={variant}>
            <th>{variant}</th>
            {STATUSES.map((status) => (
              <td key={status}>
                <Input
                  {...args}
                  variant={variant}
                  status={status}
                  value={status === "empty" ? "" : "AB12CD"}
                  placeholder="코드를 입력하세요"
                  readOnly
                />
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  ),
};
