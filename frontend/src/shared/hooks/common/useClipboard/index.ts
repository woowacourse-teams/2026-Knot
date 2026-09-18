import { useState } from "react";

interface CopyParams {
  text: string;
  onCopySuccess?: () => void;
}

const useClipboard = () => {
  const [isCopied, setIsCopied] = useState(false);

  const copy = async ({ text, onCopySuccess }: CopyParams) => {
    if (!navigator.clipboard) {
      console.error("Clipboard API가 지원되지 않는 환경입니다.");
      return;
    }

    try {
      setIsCopied(true);
      await navigator.clipboard.writeText(text);
      onCopySuccess?.();
    } catch (error) {
      console.error("텍스트 복사에 실패했습니다:", error);
    } finally {
      setIsCopied(false);
    }
  };

  return { copy, isCopied };
};

export default useClipboard;
