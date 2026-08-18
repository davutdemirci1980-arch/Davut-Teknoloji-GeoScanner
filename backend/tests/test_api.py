from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health():
    assert client.get("/api/health").json() == {"status": "ok"}


def test_materials_and_sensors_catalog():
    materials = client.get("/api/materials").json()
    assert len(materials) == 12
    sensors = client.get("/api/sensors").json()
    assert len(sensors) == 8


def test_full_scenario_flow():
    create_resp = client.post(
        "/api/scenarios",
        json={"nx": 14, "ny": 14, "nz": 8, "preset": "utility_lines", "num_anomalies": 3, "seed": 11},
    )
    assert create_resp.status_code == 200
    session_id = create_resp.json()["session_id"]

    voxels_resp = client.get(f"/api/scenarios/{session_id}/voxels")
    assert voxels_resp.status_code == 200
    assert len(voxels_resp.json()["voxels"]) > 0

    run_resp = client.post(f"/api/scenarios/{session_id}/sensors/run", json={"noise_level": 0.1, "seed": 2})
    assert run_resp.status_code == 200
    assert len(run_resp.json()["results"]) == 8

    volume_resp = client.get(f"/api/scenarios/{session_id}/sensors/gpr/volume", params={"min_probability": 0.1})
    assert volume_resp.status_code == 200

    analyze_resp = client.post(f"/api/scenarios/{session_id}/analyze", json={})
    assert analyze_resp.status_code == 200
    body = analyze_resp.json()
    assert "anomalies" in body

    fused_resp = client.get(f"/api/scenarios/{session_id}/analysis/fused_volume", params={"min_probability": 0.1})
    assert fused_resp.status_code == 200


def test_unknown_session_returns_404():
    resp = client.get("/api/scenarios/does-not-exist/voxels")
    assert resp.status_code == 404
