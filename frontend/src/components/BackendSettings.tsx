import { useState } from "react";
import { getApiBase, setApiBase } from "../api/client";

interface Props {
  onSaved: () => void;
}

export default function BackendSettings({ onSaved }: Props) {
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState(getApiBase());

  function save() {
    setApiBase(value);
    setOpen(false);
    onSaved();
  }

  return (
    <div className="backend-settings">
      <button className="ghost-btn" onClick={() => setOpen((o) => !o)}>
        Sunucu Ayarı
      </button>
      {open && (
        <div className="backend-settings-popover">
          <label>
            Backend Adresi (API)
            <input
              type="text"
              placeholder="ör. http://192.168.1.20:8000/api"
              value={value}
              onChange={(e) => setValue(e.target.value)}
            />
          </label>
          <p className="panel-hint">
            Telefonda çalıştırıyorsanız bilgisayarınızın yerel ağ IP adresini girin (aynı Wi-Fi'de olmalısınız).
            Web'de varsayılan <code>/api</code> yeterlidir.
          </p>
          <button className="primary-btn" onClick={save}>
            Kaydet ve Yeniden Yükle
          </button>
        </div>
      )}
    </div>
  );
}
