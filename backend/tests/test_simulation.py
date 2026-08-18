from app.simulation.lab import SimulationLab
from app.simulation.scenarios import generate_scenario


def test_generate_scenario_shape_and_seed_determinism():
    grid_a = generate_scenario(nx=12, ny=12, nz=8, seed=5, num_anomalies=3)
    grid_b = generate_scenario(nx=12, ny=12, nz=8, seed=5, num_anomalies=3)
    assert grid_a.shape == (12, 12, 8)
    assert (grid_a.material_ids == grid_b.material_ids).all()


def test_geology_only_preset_has_no_anomaly_materials():
    grid = generate_scenario(nx=10, ny=10, nz=8, preset="geology_only", num_anomalies=5, seed=1)
    anomaly_voxels = grid.is_anomaly_grid()
    assert not anomaly_voxels.any()


def test_lab_full_pipeline():
    lab = SimulationLab()
    session = lab.create_scenario(nx=12, ny=12, nz=8, num_anomalies=3, seed=3)

    results = lab.run_sensors(session.id, sensor_keys=["gpr", "magnetometer"], seed=1)
    assert set(results.keys()) == {"gpr", "magnetometer"}

    fusion, anomalies = lab.run_analysis(session.id)
    assert fusion.fused_probability.shape == session.grid.shape
    assert isinstance(anomalies, list)
    for anomaly in anomalies:
        assert anomaly.predicted_material
        assert 0.0 <= anomaly.classification_confidence <= 1.0


def test_run_analysis_without_sensors_raises():
    lab = SimulationLab()
    session = lab.create_scenario(nx=8, ny=8, nz=6, num_anomalies=1, seed=2)
    try:
        lab.run_analysis(session.id)
        assert False, "expected ValueError"
    except ValueError:
        pass
