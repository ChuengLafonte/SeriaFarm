# SeriaFarm Wiki

Comprehensive guide for SeriaFarm configuration and management.

## 🛠 Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/sfarm menu` | `seriafarm.admin` | Open the main configuration dashboard |
| `/sfarm reload` | `seriafarm.admin` | Reload all configurations |
| `/sfarm stats` | `seriafarm.use` | View personal farming statistics |

## 🔑 Permissions
- `seriafarm.admin`: Full access to all features and GUIs.
- `seriafarm.use`: Access to basic player features.

## ⚙️ Configuration

### crops.yml
Define block regeneration settings.
```yaml
crops:
  WHEAT:
    regen-time: 30 # Seconds
    replace-block: WHEAT
    growth-stages: 7
    drops:
      - id: mi:MATERIAL:WHEAT_BUNDLE
        chance: 0.1
```

### seeds.yml
Configure custom plants that require watering and specific soil.
```yaml
seeds:
  MAGIC_BEAN:
    name: "<green>Magic Bean"
    item: "mi:MATERIAL:MAGIC_BEAN"
    growth_time: 3600 # 1 Hour
    stages: 3
    soil: "FERTILE_SOIL"
    water_consumption: 0.1
```

## 🌍 Region Management
SeriaFarm supports regional overrides via WorldGuard.
1. Select a region using `/sfarm menu`.
2. Configure specific regen times or crop availability for that region only.
3. Save settings to `regions.yml` automatically via the GUI.

## 💧 Watering System
Custom plants require soil moisture.
- Use **Watering Cans** (MMOItems) to increase soil moisture.
- Moisture decays over time (configurable in `config.yml`).
- Plants stop growing if moisture reaches 0%.
