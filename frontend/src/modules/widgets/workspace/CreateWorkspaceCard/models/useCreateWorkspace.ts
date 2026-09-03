import useCreateWorkspaceMutation from "@api/mutations/useCreateWorkspaceMutation";
import useNavigateToLogin from "@hooks/domain/auth/useNavigateToLogin";
import useNavigateToWorkspaceInvite from "@hooks/domain/workspace/useNavigateToWorkspaceInvite";
import { isUnauthorizedError } from "@utils/isUnauthorizedError";
import { ChangeEvent, FormEvent, useRef, useState } from "react";

import { WORKSPACE_NAME_MAX_LENGTH } from "../constants/workspaceName";
import { getSubmitErrorMessage } from "../utils/getSubmitErrorMessage";
import { getWorkspaceNameErrorMessage } from "../utils/getWorkspaceNameErrorMessage";

/**
 * 워크스페이스 이름을 입력받아 생성하는 흐름.
 *
 * 에러가 두 갈래로 들어옵니다. 글자를 칠 때마다 하는 형식 검사와, 제출한 뒤 서버가 주는 응답이에요.
 * 둘을 하나의 `errorMessage`로 합쳐 입력창 아래 같은 자리에 띄웁니다.
 * 값을 다시 고치면 서버 문구는 지워요. 고친 값에 대한 판단이 아니라서요.
 *
 * 생성에 성공하면 응답의 id로 팀원 초대 화면(`/workspace/:workspaceId/invite`)으로 넘어가요.
 * 인증이 풀린 401은 문구로 알리지 않고 로그인 화면으로 돌려보냅니다.
 *
 * 제출이 실패하면 입력창으로 포커스를 되돌립니다. 버튼을 누르면서 포커스가 버튼으로
 * 옮겨갔는데, 실패로 버튼이 비활성되면 포커스가 갈 곳을 잃기 때문이에요.
 */
export const useCreateWorkspace = () => {
  const [name, setName] = useState("");
  const [submitErrorMessage, setSubmitErrorMessage] = useState<string>();
  const inputRef = useRef<HTMLInputElement>(null);

  const { mutate, isPending } = useCreateWorkspaceMutation();
  const { navigateToWorkspaceInvite } = useNavigateToWorkspaceInvite();
  const { navigateToLogin } = useNavigateToLogin();

  const errorMessage = getWorkspaceNameErrorMessage(name) ?? submitErrorMessage;
  const isSubmittable = name.trim().length > 0 && errorMessage === undefined;

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    setName(event.target.value.slice(0, WORKSPACE_NAME_MAX_LENGTH));
    setSubmitErrorMessage(undefined);
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!isSubmittable || isPending) return;

    mutate(
      { name },
      {
        onSuccess: ({ id }) => navigateToWorkspaceInvite(String(id)),
        onError: (error) => {
          if (isUnauthorizedError(error)) {
            navigateToLogin({ replace: true });
            return;
          }

          setSubmitErrorMessage(getSubmitErrorMessage(error));
          inputRef.current?.focus();
        },
      },
    );
  };

  return {
    name,
    errorMessage,
    isSubmittable,
    isPending,
    inputRef,
    handleChange,
    handleSubmit,
  };
};
