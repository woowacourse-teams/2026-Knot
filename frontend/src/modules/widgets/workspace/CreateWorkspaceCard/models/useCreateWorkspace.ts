import useNavigateToWorkspaceInvite from "@hooks/domain/workspace/useNavigateToWorkspaceInvite";
import { ChangeEvent, FormEvent, useState } from "react";

import { WORKSPACE_NAME_MAX_LENGTH } from "../constants/workspaceName";
import { getWorkspaceNameErrorMessage } from "../utils/getWorkspaceNameErrorMessage";

export const useCreateWorkspace = () => {
  const [name, setName] = useState("");
  const { navigateToWorkspaceInvite } = useNavigateToWorkspaceInvite();

  const errorMessage = getWorkspaceNameErrorMessage(name);
  const isSubmittable = name.trim().length > 0 && !errorMessage;

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    setName(event.target.value.slice(0, WORKSPACE_NAME_MAX_LENGTH));
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!isSubmittable) return;

    // TODO(#216): 워크스페이스 생성 API 연결 후 응답의 workspaceId로 교체
    const TEMP_WORKSPACE_ID = "temp";
    navigateToWorkspaceInvite(TEMP_WORKSPACE_ID);
  };

  return {
    name,
    errorMessage,
    isSubmittable,
    handleChange,
    handleSubmit,
  };
};
